package com.example.pat.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.example.pat.CompanionForegroundServiceHolder
import com.example.pat.MainActivity
import com.example.pat.data.EventConfigRepository
import com.example.pat.data.PresetRepository
import com.example.pat.data.EventDefinitionRepository
import com.example.pat.detector.DropDetector
import com.example.pat.detector.DropResult
import com.example.pat.detector.ImpactDetector
import com.example.pat.detector.ImpactResult
import com.example.pat.detector.ShakeDetector
import com.example.pat.engine.DeviceStateProvider
import com.example.pat.engine.PriorityResolver
import com.example.pat.engine.RuleEngineV2
import com.example.pat.event.AtomicEvent
import com.example.pat.event.AtomicEventBus
import com.example.pat.event.EventBus
import com.example.pat.event.SensorDataBus
import com.example.pat.event.EventType
import com.example.pat.model.EventConfig
import com.example.pat.model.ReactionItem
import com.example.pat.monitor.DeviceStateMonitor
import com.example.pat.response.ResponseManager
import com.example.pat.sensor.AccelData
import com.example.pat.sensor.MotionSensorManager
import com.example.pat.sensor.SensorCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * MotionPet 前台服务 —— 事件监测与反馈的总入口。
 *
 * v3 架构（统一管道）：
 * ```
 * CompanionForegroundService
 *   │
 *   ├── DeviceStateMonitor（Screen + Battery）
 *   │     └── AtomicEventBus（唯一事件总线）
 *   │
 *   ├── Sensor Pipeline（加速度计 → Detectors）
 *   │     └── AtomicEventBus（统一发射）
 *   │
 *   ├── RuleEngineV2（统一规则引擎）
 *   │     ├── EventConfig（基础事件 → 隐式规则）
 *   │     ├── UserRule（自定义规则 → 显式规则）
 *   │     └── PriorityResolver → 全局优先级排序
 *   │           └── onRuleMatched → ResponseManager（统一反馈）
 *   │
 *   └── 前台通知（常驻）
 * ```
 *
 * 废弃组件（保留但不再使用）：
 * - EventDispatcher（旧管道分发器）
 * - RuleEngine（旧版 1:1 规则引擎）
 * - EventBus（DeviceEvent 旧总线，仅保留用于 UI 日志）
 */
class CompanionForegroundService : Service() {

    // ── 设备状态监控 ──
    private lateinit var deviceStateMonitor: DeviceStateMonitor

    // ── 传感器管道 ──
    private lateinit var sensorManager: MotionSensorManager
    private val impactDetector = ImpactDetector()
    private val shakeDetector = ShakeDetector()
    private val dropDetector = DropDetector()

    // ── 数据层 ──
    private lateinit var presetRepository: PresetRepository
    private lateinit var configRepository: EventConfigRepository
    private lateinit var definitionRepository: EventDefinitionRepository

    // ── 引擎（v3 统一） ──
    lateinit var ruleEngineV2: RuleEngineV2
        private set
    lateinit var responseManager: ResponseManager
        private set

    /** 公开仓库供 UI 层查询 */
    val presetRepo: PresetRepository get() = presetRepository
    val configRepo: EventConfigRepository get() = configRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // 旧组件引用（保留兼容，不再主动启动）
    // EventDispatcher 和 RuleEngine 不再使用

    // ══════════════════════════════════════════════════════════════
    // 生命周期
    // ══════════════════════════════════════════════════════════════

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "╔══ MotionPet Service onCreate (v3 unified pipeline) ══╗")

        createNotificationChannel()

        // 初始化数据层
        presetRepository = PresetRepository(this)
        configRepository = EventConfigRepository(this, presetRepository)
        definitionRepository = EventDefinitionRepository(this)

        // 读取用户配置的阈值
        val lowBatteryThreshold = configRepository
            .getByEventType(EventType.LOW_BATTERY)?.threshold
            ?: EventConfig.defaultThreshold(EventType.LOW_BATTERY)
        val longUsageThreshold = configRepository
            .getByEventType(EventType.SCREEN_LONG_USAGE)?.threshold
            ?: EventConfig.defaultThreshold(EventType.SCREEN_LONG_USAGE)

        // 初始化设备状态监控器
        deviceStateMonitor = DeviceStateMonitor(this, serviceScope,
            lowBatteryPercent = lowBatteryThreshold,
            longUsageMinutes = longUsageThreshold)

        // 初始化传感器管理器
        sensorManager = MotionSensorManager(this)

        // 初始化响应系统
        responseManager = ResponseManager(this, presetRepository)

        // 初始化统一规则引擎（v3：基础事件 + 自定义规则）
        val stateProvider = ServiceDeviceStateProvider(this)
        ruleEngineV2 = RuleEngineV2(
            configRepository = configRepository,
            definitionRepository = definitionRepository,
            presetRepository = presetRepository,
            scope = serviceScope,
            stateProvider = stateProvider
        )
        ruleEngineV2.onRuleMatched = { matchedRule ->
            Log.i(TAG, "Rule matched: \"${matchedRule.displayName}\" (priority=${matchedRule.priority})")
            executeReaction(matchedRule)
        }

        // 注册传感器检测管道（只向 AtomicEventBus 发射）
        registerSensorPipeline()

        CompanionForegroundServiceHolder.instance = this

        Log.i(TAG, "All components initialized (v3 unified)")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand (action=${intent?.action ?: "(none)"})")

        when (intent?.action) {
            ACTION_START_SENSOR -> { startSensors(); return START_STICKY }
            ACTION_STOP_SENSOR -> { stopSensors(); return START_STICKY }
            ACTION_STOP_AUDIO -> {
                com.example.pat.audio.AudioPlaybackState.onStop()
                return START_STICKY
            }
        }

        try {
            startForeground(NOTIFICATION_ID, buildNotification())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground notification", e)
        }

        deviceStateMonitor.start()
        startSensors()
        ruleEngineV2.start()  // v3: 统一引擎处理所有规则

        Log.i(TAG, "══ MotionPet fully started (v3 unified) ══")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.i(TAG, "╚══ MotionPet Service onDestroy ══╝")
        ruleEngineV2.stop()
        deviceStateMonitor.stop()
        sensorManager.stopListening(); sensorManager.release()
        SensorDataBus.setRunning(false)
        CompanionForegroundServiceHolder.instance = null
        serviceScope.cancel()
        super.onDestroy()
    }

    // ══════════════════════════════════════════════════════════════
    // 传感器管道（只向 AtomicEventBus 发射）
    // ══════════════════════════════════════════════════════════════

    private fun startSensors() {
        sensorManager.startListening()
        SensorDataBus.setRunning(sensorManager.isRunning)
        Log.i(TAG, "Sensors ${if (sensorManager.isRunning) "started" else "failed to start"}")
    }

    private fun stopSensors() {
        sensorManager.stopListening()
        SensorDataBus.setRunning(false)
    }

    private fun registerSensorPipeline() {
        sensorManager.registerCallback(object : SensorCallback {
            override fun onSensorChanged(data: AccelData) {
                SensorDataBus.tryEmit(data)
                val now = System.currentTimeMillis()

                // 优先级：Drop > Impact > Shake
                // 跌落检测优先，避免跌落冲击被误判为撞击
                val drop = dropDetector.process(data)
                if (drop is DropResult.Detected) {
                    AtomicEventBus.tryEmit(AtomicEvent.Drop(now, drop.impactForce))
                    // 旧总线保留（UI 日志用）
                    EventBus.tryEmit(com.example.pat.event.DeviceEvent.Drop(drop.impactForce))
                    return
                }

                val impact = impactDetector.process(data)
                if (impact is ImpactResult.Detected) {
                    AtomicEventBus.tryEmit(AtomicEvent.Impact(now, impact.intensity))
                    EventBus.tryEmit(com.example.pat.event.DeviceEvent.Impact(impact.intensity))
                }

                val shake = shakeDetector.process(data)
                if (shake) {
                    AtomicEventBus.tryEmit(AtomicEvent.Shake(now))
                    EventBus.tryEmit(com.example.pat.event.DeviceEvent.Shake)
                }
            }
            override fun onAccuracyChanged(sensorType: Int, accuracy: Int) {
                Log.d(TAG, "Sensor accuracy changed: type=$sensorType accuracy=$accuracy")
            }
        })
    }

    // ══════════════════════════════════════════════════════════════
    // 统一反馈执行
    // ══════════════════════════════════════════════════════════════

    /**
     * 执行规则匹配后的反馈。
     * v3: 统一通过 ResponseManager 执行，享受全局互斥锁保护。
     */
    private fun executeReaction(matchedRule: PriorityResolver.MatchedRule) {
        val displayText = responseManager.execute(
            reactions = matchedRule.reactions,
            notification = matchedRule.notification
        )
        if (displayText != null) {
            Log.i(TAG, "Reaction executed: \"${matchedRule.displayName}\" → \"$displayText\"")
        } else {
            Log.d(TAG, "Reaction skipped (busy or empty): \"${matchedRule.displayName}\"")
        }
    }

    // ══════════════════════════════════════════════════════════════
    // DeviceStateProvider 实现
    // ══════════════════════════════════════════════════════════════

    /**
     * 服务级设备状态提供者 —— 供 [ConditionEvaluator] 查询实时设备状态。
     */
    private class ServiceDeviceStateProvider(
        private val context: Context
    ) : DeviceStateProvider {
        override val isScreenOn: Boolean
            get() {
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                return pm.isInteractive
            }

        override val isCharging: Boolean
            get() {
                val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                val intent = context.registerReceiver(null, filter)
                val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                return status == BatteryManager.BATTERY_STATUS_CHARGING
                        || status == BatteryManager.BATTERY_STATUS_FULL
            }
    }

    // ══════════════════════════════════════════════════════════════
    // 通知
    // ══════════════════════════════════════════════════════════════

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW).apply {
                description = CHANNEL_DESCRIPTION; setShowBadge(false)
            }
        )
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(NOTIFICATION_TITLE).setContentText(NOTIFICATION_TEXT)
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentIntent(pi).setOngoing(true).build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle(NOTIFICATION_TITLE).setContentText(NOTIFICATION_TEXT)
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentIntent(pi).setOngoing(true).build()
        }
    }

    companion object {
        private const val TAG = "MotionPet"
        private const val CHANNEL_ID = "motionpet_service"
        private const val CHANNEL_NAME = "MotionPet Service"
        private const val CHANNEL_DESCRIPTION = "MotionPet 后台监测服务"
        private const val NOTIFICATION_TITLE = "MotionPet"
        private const val NOTIFICATION_TEXT = "正在监测设备状态..."
        private const val NOTIFICATION_ID = 1001
        const val ACTION_START_SENSOR = "com.example.pat.START_SENSOR"
        const val ACTION_STOP_SENSOR = "com.example.pat.STOP_SENSOR"
        const val ACTION_STOP_AUDIO = "com.example.pat.STOP_AUDIO"
    }
}
