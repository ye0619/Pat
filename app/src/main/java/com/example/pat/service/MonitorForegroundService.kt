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
import com.example.pat.MainActivity
import com.example.pat.detector.DropDetector
import com.example.pat.detector.DropResult
import com.example.pat.detector.ImpactDetector
import com.example.pat.detector.ImpactResult
import com.example.pat.detector.ShakeDetector
import com.example.pat.event.DeviceEvent
import com.example.pat.event.EventBus
import com.example.pat.event.SensorDataBus
import com.example.pat.monitor.DeviceStateMonitor
import com.example.pat.sensor.AccelData
import com.example.pat.sensor.MotionSensorManager
import com.example.pat.sensor.SensorCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 前台服务 —— 系统的唯一事件入口。
 *
 * 托管所有数据源，Activity 仅负责 UI 展示：
 * ```
 * MonitorForegroundService（唯一事件入口）
 *   │
 *   ├── DeviceStateMonitor
 *   │     ├── ScreenMonitor  → 屏幕亮灭 → EventBus
 *   │     └── BatteryMonitor → 电池状态 → EventBus
 *   │
 *   └── Sensor Pipeline
 *         ├── MotionSensorManager → 加速度计 → SensorDataBus
 *         ├── ImpactDetector      → 拍击    → EventBus
 *         ├── ShakeDetector       → 摇晃    → EventBus
 *         └── DropDetector        → 跌落    → EventBus
 *
 *                ↓
 *   EventBus ←── 所有 DeviceEvent ──→ Activity (UI)
 *   SensorDataBus ←── AccelData ────→ Activity (UI)
 * ```
 *
 * 生命周期：
 * - onCreate: 初始化所有监控器和检测器
 * - onStartCommand: 启动前台通知 + 启动所有监控
 * - onDestroy: 停止所有监控 + 释放资源
 * - START_STICKY: 被杀后自动重建
 */
class MonitorForegroundService : Service() {

    // ── 设备状态监控 ──
    private lateinit var deviceStateMonitor: DeviceStateMonitor

    // ── 传感器管道 ──
    private lateinit var sensorManager: MotionSensorManager
    private val impactDetector = ImpactDetector()
    private val shakeDetector = ShakeDetector()
    private val dropDetector = DropDetector()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var deviceEventForwardJob: Job? = null

    // ══════════════════════════════════════════════════════════════
    // 生命周期
    // ══════════════════════════════════════════════════════════════

    override fun onCreate() {
        super.onCreate()
        Log.w(TAG, "╔══ Service onCreate — initializing all monitors ══╗")
        Log.i(TAG, "Service instance: ${this.javaClass.simpleName}@${System.identityHashCode(this)}")

        createNotificationChannel()

        // 初始化设备状态监控器（ScreenMonitor + BatteryMonitor）
        deviceStateMonitor = DeviceStateMonitor(this, serviceScope)
        Log.i(TAG, "DeviceStateMonitor initialized (Screen + Battery)")

        // 初始化传感器管理器
        sensorManager = MotionSensorManager(this)
        Log.i(TAG, "MotionSensorManager initialized")

        // 注册传感器检测管道（后台线程 → detectors → EventBus）
        registerSensorPipeline()
        Log.i(TAG, "Sensor pipeline registered (Impact + Shake + Drop detectors)")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "Service onStartCommand (flags=$flags, startId=$startId, " +
                "action=${intent?.action ?: "(none)"})")

        // 处理手动启停传感器命令（来自 UI 按钮）
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
            Log.i(TAG, "Foreground notification shown (id=$NOTIFICATION_ID)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground notification", e)
        }

        // 启动设备监控（ScreenMonitor + BatteryMonitor）
        deviceStateMonitor.start()
        Log.i(TAG, "DeviceStateMonitor started")

        // 启动传感器采集 + 检测
        startSensors()

        // 转发设备事件到 EventBus
        startDeviceEventForwarding()

        Log.i(TAG, "══ Service fully started — all 3 monitors active ══")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.w(TAG, "╚══ Service onDestroy — releasing all resources ══╝")

        // 停止前最后检查 LONG_USAGE
        deviceStateMonitor.screenMonitor.checkLongUsage()

        // 停止事件转发
        deviceEventForwardJob?.cancel()
        deviceEventForwardJob = null

        // 停止设备监控
        deviceStateMonitor.stop()
        Log.i(TAG, "DeviceStateMonitor stopped")

        // 停止传感器
        sensorManager.stopListening()
        sensorManager.release()
        SensorDataBus.setRunning(false)
        Log.i(TAG, "Sensor pipeline stopped")

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
     *
     * 运行在传感器事件线程（后台）。
     * SensorDataBus.tryEmit() 和 EventBus.tryEmit() 均为线程安全非阻塞调用。
     */
    private fun registerSensorPipeline() {
        sensorManager.registerCallback(object : SensorCallback {
            override fun onSensorChanged(data: AccelData) {
                // 原始数据 → SensorDataBus（UI 实时显示用）
                SensorDataBus.tryEmit(data)

                // 检测管道 → EventBus（行为响应用）
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
        private const val TAG = "MotionPetScreenMonitor"

        // 通知渠道
        private const val CHANNEL_ID = "monitor_service"
        private const val CHANNEL_NAME = "Companion Service"
        private const val CHANNEL_DESCRIPTION = "Shows when device monitoring is active"
        private const val NOTIFICATION_TITLE = "Pat Companion"
        private const val NOTIFICATION_TEXT = "Monitoring sensors + screen + battery…"
        private const val NOTIFICATION_ID = 1001

        // 手动控制传感器命令
        const val ACTION_START_SENSOR = "com.example.pat.START_SENSOR"
        const val ACTION_STOP_SENSOR = "com.example.pat.STOP_SENSOR"
    }
}
