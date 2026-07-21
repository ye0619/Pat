package com.example.pat.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.example.pat.CompanionForegroundServiceHolder
import com.example.pat.MainActivity
import com.example.pat.data.EventConfigRepository
import com.example.pat.data.PresetRepository
import com.example.pat.data.UserRuleRepository
import com.example.pat.detector.DropDetector
import com.example.pat.detector.DropResult
import com.example.pat.detector.ImpactDetector
import com.example.pat.detector.ImpactResult
import com.example.pat.detector.ShakeDetector
import com.example.pat.engine.EventDispatcher
import com.example.pat.engine.RuleEngine
import com.example.pat.engine.RuleEngineV2
import com.example.pat.event.AtomicEvent
import com.example.pat.event.AtomicEventBus
import com.example.pat.event.DeviceEvent
import com.example.pat.event.EventBus
import com.example.pat.event.EventType
import com.example.pat.event.SensorDataBus
import com.example.pat.model.EventConfig
import com.example.pat.monitor.DeviceStateMonitor
import com.example.pat.model.UserRule
import com.example.pat.response.ResponseManager
import com.example.pat.sensor.AccelData
import com.example.pat.sensor.MotionSensorManager
import com.example.pat.sensor.SensorCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * MotionPet 前台服务 —— 事件监测与反馈的总入口。
 *
 * 架构（v2）：
 * ```
 * CompanionForegroundService
 *   │
 *   ├── DeviceStateMonitor（Screen + Battery）
 *   │     └── EventBus
 *   │
 *   ├── Sensor Pipeline（加速度计 → Detectors）
 *   │     └── EventBus
 *   │
 *   ├── EventDispatcher（从 EventBus 收集）
 *   │     └── RuleEngine（匹配 EventConfig）
 *   │           └── ResponseManager
 *   │                 ├── EventConfig.presetId → PresetRepository.getById()
 *   │                 ├── ReactionPreset.text → NotificationService
 *   │                 └── ReactionPreset.audioAssetPath → AudioPlayer / VoiceService
 *   │
 *   └── 前台通知（常驻）
 * ```
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
    private lateinit var userRuleRepository: UserRuleRepository

    // ── 引擎 ──
    private lateinit var ruleEngine: RuleEngine
    /** 新规则引擎 v2（用户自定义组合规则） */
    lateinit var ruleEngineV2: RuleEngineV2
        private set
    lateinit var responseManager: ResponseManager
        private set
    lateinit var eventDispatcher: EventDispatcher
        private set

    /** 公开仓库供 UI 层查询 */
    val presetRepo: PresetRepository get() = presetRepository
    val configRepo: EventConfigRepository get() = configRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var deviceEventForwardJob: Job? = null
    private var longUsageCheckJob: Job? = null

    // ══════════════════════════════════════════════════════════════
    // 生命周期
    // ══════════════════════════════════════════════════════════════

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "╔══ MotionPet Service onCreate ══╗")

        createNotificationChannel()

        // 初始化数据层
        presetRepository = PresetRepository(this)
        configRepository = EventConfigRepository(this, presetRepository)
        userRuleRepository = UserRuleRepository(this)

        // 读取用户配置的低电量阈值
        val lowBatteryThreshold = configRepository
            .getByEventType(EventType.LOW_BATTERY)?.threshold
            ?: EventConfig.defaultThreshold(EventType.LOW_BATTERY)

        // 初始化设备状态监控器
        deviceStateMonitor = DeviceStateMonitor(this, serviceScope, lowBatteryPercent = lowBatteryThreshold)

        // 初始化传感器管理器
        sensorManager = MotionSensorManager(this)

        // 初始化响应系统
        responseManager = ResponseManager(this, presetRepository)

        // 初始化规则引擎
        ruleEngine = RuleEngine(configRepository)

        // 初始化事件分发器
        eventDispatcher = EventDispatcher(ruleEngine, responseManager, serviceScope)

        // 初始化新规则引擎 v2（用户自定义组合规则）
        ruleEngineV2 = RuleEngineV2(userRuleRepository, scope = serviceScope)
        ruleEngineV2.onRuleMatched = { rule ->
            Log.i(TAG, "RuleEngineV2 matched: \"${rule.name}\" — executing reaction")
            // 使用 EventBus 桥接：将匹配的规则转为 DeviceEvent 触发反馈
            // 规则自带 reactionText + reactionAudioPath，由 executeRuleReaction 处理
            executeRuleReaction(rule)
        }

        // 注册传感器检测管道
        registerSensorPipeline()

        CompanionForegroundServiceHolder.instance = this

        Log.i(TAG, "All components initialized")
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
        startDeviceEventForwarding()
        eventDispatcher.start()
        ruleEngineV2.start()
        startLongUsageCheck()

        Log.i(TAG, "══ MotionPet fully started ══")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.i(TAG, "╚══ MotionPet Service onDestroy ══╝")
        eventDispatcher.stop()
        ruleEngineV2.stop()
        longUsageCheckJob?.cancel(); longUsageCheckJob = null
        deviceEventForwardJob?.cancel(); deviceEventForwardJob = null
        deviceStateMonitor.stop()
        sensorManager.stopListening(); sensorManager.release()
        SensorDataBus.setRunning(false)
        CompanionForegroundServiceHolder.instance = null
        serviceScope.cancel()
        super.onDestroy()
    }

    // ══════════════════════════════════════════════════════════════
    // 传感器管道
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
                    EventBus.tryEmit(DeviceEvent.Drop(drop.impactForce))
                    AtomicEventBus.tryEmit(AtomicEvent.Drop(now, drop.impactForce))
                    return
                }

                val impact = impactDetector.process(data)
                if (impact is ImpactResult.Detected) {
                    EventBus.tryEmit(DeviceEvent.Impact(impact.intensity))
                    AtomicEventBus.tryEmit(AtomicEvent.Impact(now, impact.intensity))
                }

                val shake = shakeDetector.process(data)
                if (shake) {
                    EventBus.tryEmit(DeviceEvent.Shake)
                    AtomicEventBus.tryEmit(AtomicEvent.Shake(now))
                }
            }
            override fun onAccuracyChanged(sensorType: Int, accuracy: Int) {
                Log.d(TAG, "Sensor accuracy changed: type=$sensorType accuracy=$accuracy")
            }
        })
    }

    /**
     * 执行用户自定义规则的反馈。
     * 桥接 UserRule → 现有 ResponseManager + NotificationService。
     * 优先使用规则自带的 reactionText/reactionAudioPath。
     */
    private fun executeRuleReaction(rule: UserRule) {
        val displayText = rule.reactionText.ifBlank { "\"${rule.name}\" 已触发" }

        // 通知
        if (rule.notificationEnabled && displayText.isNotBlank()) {
            val ns = com.example.pat.response.NotificationService(this)
            ns.show(
                title = "Pat",
                text = displayText,
                enableSound = rule.soundEnabled,
                enableVibration = rule.vibrationEnabled,
                showHeadsUp = rule.showHeadsUp,
                lockScreenPublic = rule.lockScreenPublic
            )
            Log.i(TAG, "Rule reaction: \"${rule.name}\" → \"$displayText\"")
        }

        // 音频
        val audioPath = rule.reactionAudioPath
        if (audioPath.isNotBlank()) {
            if (audioPath.startsWith("/") || audioPath.startsWith(filesDir.absolutePath)) {
                com.example.pat.response.VoiceService(this).play(audioPath)
            } else {
                com.example.pat.audio.AudioPlayer(this).playAsset(audioPath)
            }
        }
    }

    private fun startDeviceEventForwarding() {
        deviceEventForwardJob?.cancel()
        deviceEventForwardJob = serviceScope.launch {
            deviceStateMonitor.events.collect { EventBus.tryEmit(it) }
        }
    }

    private fun startLongUsageCheck() {
        longUsageCheckJob?.cancel()
        longUsageCheckJob = serviceScope.launch {
            while (isActive) {
                delay(60_000L)
                val thresholdMin = configRepository.getByEventType(EventType.SCREEN_LONG_USAGE)?.threshold ?: 120
                deviceStateMonitor.screenMonitor.checkLongUsage(thresholdMin)
            }
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
