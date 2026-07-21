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
import com.example.pat.config.PreferenceManager
import com.example.pat.detector.DropDetector
import com.example.pat.detector.DropResult
import com.example.pat.detector.ImpactDetector
import com.example.pat.detector.ImpactResult
import com.example.pat.detector.ShakeDetector
import com.example.pat.engine.EventDispatcher
import com.example.pat.engine.RuleEngine
import com.example.pat.event.DeviceEvent
import com.example.pat.event.EventBus
import com.example.pat.event.EventType
import com.example.pat.event.SensorDataBus
import com.example.pat.monitor.DeviceStateMonitor
import com.example.pat.preset.PresetLoader
import com.example.pat.preset.PresetRepository
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
 * 架构：
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
 *   │     └── RuleEngine（匹配配置）
 *   │           └── ResponseManager（执行反馈）
 *   │                 ├── NotificationService
 *   │                 └── VoiceService
 *   │
 *   └── 前台通知（常驻）
 * ```
 *
 * Activity 仅负责 UI 配置，不依赖 Activity 生命周期。
 *
 * 参考文档：原始规范 6. 后台架构
 */
class CompanionForegroundService : Service() {

    // ── 设备状态监控 ──
    private lateinit var deviceStateMonitor: DeviceStateMonitor

    // ── 传感器管道 ──
    private lateinit var sensorManager: MotionSensorManager
    private val impactDetector = ImpactDetector()
    private val shakeDetector = ShakeDetector()
    private val dropDetector = DropDetector()

    // ── 配置与引擎 ──
    private lateinit var preferenceManager: PreferenceManager
    private lateinit var presetRepository: PresetRepository
    private lateinit var ruleEngine: RuleEngine
    private lateinit var responseManager: ResponseManager
    lateinit var eventDispatcher: EventDispatcher
        private set

    /** 公开预设仓库，供 UI 层查询预设列表 */
    val presetRepo: PresetRepository
        get() = presetRepository

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

        // 初始化配置
        preferenceManager = PreferenceManager(this)

        // 初始化预设系统
        val presetLoader = PresetLoader(this)
        presetRepository = PresetRepository(presetLoader)

        // 初始化设备状态监控器
        deviceStateMonitor = DeviceStateMonitor(this, serviceScope)

        // 初始化传感器管理器
        sensorManager = MotionSensorManager(this)

        // 初始化响应系统（接入预设仓库）
        responseManager = ResponseManager(this, presetRepository)

        // 初始化规则引擎
        ruleEngine = RuleEngine(preferenceManager)

        // 初始化事件分发器
        eventDispatcher = EventDispatcher(ruleEngine, responseManager, serviceScope)

        // 注册传感器检测管道
        registerSensorPipeline()

        // 注册到全局 Holder（供 Activity 获取状态）
        CompanionForegroundServiceHolder.instance = this

        Log.i(TAG, "All components initialized")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand (action=${intent?.action ?: "(none)"})")

        // 处理手动启停传感器命令
        when (intent?.action) {
            ACTION_START_SENSOR -> {
                startSensors()
                return START_STICKY
            }
            ACTION_STOP_SENSOR -> {
                stopSensors()
                return START_STICKY
            }
        }

        // ── 正常启动流程 ──

        // 前台通知
        try {
            startForeground(NOTIFICATION_ID, buildNotification())
            Log.i(TAG, "Foreground notification shown")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground notification", e)
        }

        // 启动设备监控（Screen + Battery）
        deviceStateMonitor.start()

        // 启动传感器采集 + 检测
        startSensors()

        // 转发设备事件到 EventBus
        startDeviceEventForwarding()

        // 启动事件分发 → 规则引擎 → 反馈
        eventDispatcher.start()

        // 启动周期性屏幕使用时长检查（每 1 分钟）
        startLongUsageCheck()

        Log.i(TAG, "══ MotionPet fully started ══")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.i(TAG, "╚══ MotionPet Service onDestroy ══╝")

        // 停止事件分发
        eventDispatcher.stop()

        // 停止周期性检查
        longUsageCheckJob?.cancel()
        longUsageCheckJob = null

        // 停止事件转发
        deviceEventForwardJob?.cancel()
        deviceEventForwardJob = null

        // 停止设备监控
        deviceStateMonitor.stop()

        // 停止传感器
        sensorManager.stopListening()
        sensorManager.release()
        SensorDataBus.setRunning(false)

        // 清除全局引用
        CompanionForegroundServiceHolder.instance = null

        // 释放协程
        serviceScope.cancel()

        super.onDestroy()
    }

    // ══════════════════════════════════════════════════════════════
    // 传感器管道
    // ══════════════════════════════════════════════════════════════

    private fun startSensors() {
        sensorManager.startListening()
        val running = sensorManager.isRunning
        SensorDataBus.setRunning(running)
        Log.i(TAG, "Sensors ${if (running) "started" else "failed to start"}")
    }

    private fun stopSensors() {
        sensorManager.stopListening()
        SensorDataBus.setRunning(false)
        Log.i(TAG, "Sensors stopped")
    }

    /**
     * 注册传感器回调 → 原始数据写入 SensorDataBus，检测事件写入 EventBus。
     */
    private fun registerSensorPipeline() {
        sensorManager.registerCallback(object : SensorCallback {
            override fun onSensorChanged(data: AccelData) {
                SensorDataBus.tryEmit(data)

                val impact = impactDetector.process(data)
                val shake = shakeDetector.process(data)
                val drop = dropDetector.process(data)

                if (impact is ImpactResult.Detected) {
                    EventBus.tryEmit(DeviceEvent.Impact(impact.intensity))
                }
                if (shake) {
                    EventBus.tryEmit(DeviceEvent.Shake)
                }
                if (drop is DropResult.Detected) {
                    EventBus.tryEmit(DeviceEvent.Drop(drop.impactForce))
                }
            }

            override fun onAccuracyChanged(sensorType: Int, accuracy: Int) {
                Log.d(TAG, "Sensor accuracy changed: type=$sensorType accuracy=$accuracy")
            }
        })
    }

    // ══════════════════════════════════════════════════════════════
    // 设备事件转发
    // ══════════════════════════════════════════════════════════════

    private fun startDeviceEventForwarding() {
        deviceEventForwardJob?.cancel()
        deviceEventForwardJob = serviceScope.launch {
            deviceStateMonitor.events.collect { event ->
                EventBus.tryEmit(event)
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 周期性屏幕使用检查
    // ══════════════════════════════════════════════════════════════

    /**
     * 每 1 分钟检查一次屏幕累计使用时长。
     * 从配置中读取用户设定的阈值，超过时通过 EventBus 发射 LongUsage 事件。
     */
    private fun startLongUsageCheck() {
        longUsageCheckJob?.cancel()
        longUsageCheckJob = serviceScope.launch {
            while (isActive) {
                delay(60_000L) // 每 1 分钟检查
                val config = preferenceManager.getConfig(EventType.SCREEN_LONG_USAGE)
                val thresholdMin = config?.threshold ?: 120
                deviceStateMonitor.screenMonitor.checkLongUsage(thresholdMin)
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 通知
    // ══════════════════════════════════════════════════════════════

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = CHANNEL_DESCRIPTION
            setShowBadge(false)
        }

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(NOTIFICATION_TITLE)
                .setContentText(NOTIFICATION_TEXT)
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle(NOTIFICATION_TITLE)
                .setContentText(NOTIFICATION_TEXT)
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        }
    }

    companion object {
        private const val TAG = "MotionPet"

        // 通知渠道
        private const val CHANNEL_ID = "motionpet_service"
        private const val CHANNEL_NAME = "MotionPet Service"
        private const val CHANNEL_DESCRIPTION = "MotionPet 后台监测服务"
        private const val NOTIFICATION_TITLE = "MotionPet"
        private const val NOTIFICATION_TEXT = "正在监测设备状态..."
        private const val NOTIFICATION_ID = 1001

        // 手动控制传感器命令
        const val ACTION_START_SENSOR = "com.example.pat.START_SENSOR"
        const val ACTION_STOP_SENSOR = "com.example.pat.STOP_SENSOR"
    }
}
