package com.example.pat

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import com.example.pat.detector.DropDetector
import com.example.pat.detector.DropResult
import com.example.pat.detector.ImpactDetector
import com.example.pat.detector.ImpactResult
import com.example.pat.detector.ShakeDetector
import com.example.pat.event.DeviceEvent
import com.example.pat.event.DeviceEventLogEntry
import com.example.pat.event.EventBus
import com.example.pat.event.toDisplayLabel
import com.example.pat.service.MonitorForegroundService
import com.example.pat.sensor.AccelData
import com.example.pat.sensor.MotionSensorManager
import com.example.pat.sensor.SensorCallback
import com.example.pat.ui.SensorDebugScreen
import com.example.pat.ui.theme.PatTheme
import com.example.pat.util.PermissionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 主 Activity。
 *
 * 职责：
 * - 管理 MotionSensorManager 生命周期（传感器数据采集 + 动作检测）
 * - 启动 MonitorForegroundService 托管设备状态监控（屏幕 + 电池）
 * - 统一从 EventBus 收集所有事件并更新 UI
 *
 * 生命周期设计：
 * ```
 * onCreate → 初始化传感器 + 启动 ForegroundService
 * onStart  → 开始传感器采集 + 开始 EventBus 收集
 * onStop   → 停止 EventBus 收集 + 停止传感器采集
 * onDestroy→ 释放传感器资源 + 取消协程
 * ```
 *
 * 关键变化（v2）：
 * - DeviceStateMonitor 移入 MonitorForegroundService，不再依赖 Activity 生命周期
 * - 所有事件（传感器 + 设备状态）通过 EventBus 统一收集
 * - 消除 mainHandler.post，EventBus.tryEmit() 天然线程安全
 *
 * 数据流：
 * ```
 * Sensor → detectors → EventBus.tryEmit() ─┐
 *                                           ├→ EventBus.events → UI
 * Service → DeviceStateMonitor → EventBus ──┘
 * ```
 *
 * 参考文档：3.2 分层架构
 */
class MainActivity : ComponentActivity() {

    // ── 传感器 ──
    private lateinit var sensorManager: MotionSensorManager
    private val impactDetector = ImpactDetector()
    private val shakeDetector = ShakeDetector()
    private val dropDetector = DropDetector()

    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // ── UI 状态 ──
    private val lastEvent = mutableStateOf<DeviceEvent?>(null)
    private val eventLog = mutableStateListOf<DeviceEventLogEntry>()
    private val maxEventLogSize = 50
    /** Compose 可观察的传感器运行状态 */
    private val isSensorRunning = mutableStateOf(false)

    /** UI 使用的传感器数据流（已降频，避免 GPU 过载） */
    private lateinit var uiSensorDataFlow: Flow<AccelData>

    /** 诊断计数器：每秒自增约 10 次（基于降频后的 uiSensorDataFlow），验证 pipeline 活动性 */
    private val sensorFrameCount = mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 初始化传感器管理器
        sensorManager = MotionSensorManager(this)

        // 降频传感器数据流用于 UI 显示（10fps，避免 GPU BufferQueue 过载）
        @OptIn(FlowPreview::class)
        val sampled = sensorManager.sensorData.sample(100)
        uiSensorDataFlow = sampled

        // 诊断计数器跟随降频流（约 10fps）
        activityScope.launch {
            uiSensorDataFlow.collect {
                sensorFrameCount.value = (sensorFrameCount.value + 1) % 1000
            }
        }

        // 请求运行时权限
        requestNotificationPermission()

        // 建立传感器检测管道
        startSensorPipeline()

        // 启动后台 ForegroundService（托管 ScreenMonitor + BatteryMonitor）
        // Service 有独立生命周期，不随 Activity onStop 而停止
        startMonitorService()

        setContent {
            PatTheme {
                SensorDebugScreen(
                    isSensorRunning = isSensorRunning.value,
                    sensorDataFlow = uiSensorDataFlow,
                    latestData = sensorManager.latestData,
                    lastEvent = lastEvent.value,
                    sensorFrameCount = sensorFrameCount.value,
                    eventLog = eventLog.toList(),
                    onStartClick = {
                        sensorManager.startListening()
                        isSensorRunning.value = sensorManager.isRunning
                    },
                    onStopClick = {
                        sensorManager.stopListening()
                        isSensorRunning.value = sensorManager.isRunning
                    },
                    onTestEventClick = {
                        Log.i(TAG, "=== Test button pressed ===")
                        EventBus.tryEmit(DeviceEvent.Shake)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Log.i(TAG, "MainActivity created (hash=${hashCode()})")
    }

    override fun onStart() {
        super.onStart()
        // 可见：启动传感器采集 + 开始从 EventBus 收集事件更新 UI
        sensorManager.startListening()
        isSensorRunning.value = sensorManager.isRunning
        startEventCollection()
        Log.i(TAG, "onStart: sensors started, event collection started")
    }

    override fun onStop() {
        // 不可见：停止 EventBus 收集 + 传感器采集（省电）
        // 注意：不停止 MonitorForegroundService！Service 继续在后台运行
        collectionJob?.cancel()
        collectionJob = null
        sensorManager.stopListening()
        isSensorRunning.value = sensorManager.isRunning
        Log.i(TAG, "onStop: event collection stopped, sensors stopped (service continues)")
        super.onStop()
    }

    override fun onDestroy() {
        collectionJob?.cancel()
        activityScope.cancel()
        if (::sensorManager.isInitialized) {
            sensorManager.release()
        }
        Log.i(TAG, "onDestroy: hash=${hashCode()} resources released")
        super.onDestroy()
    }

    // ══════════════════════════════════════════════════════════════
    // ForegroundService 管理
    // ══════════════════════════════════════════════════════════════

    /**
     * 启动 MonitorForegroundService。
     *
     * Service 内部托管 DeviceStateMonitor（ScreenMonitor + BatteryMonitor），
     * 所有设备事件通过 EventBus 分发。
     * Service 在后台持续运行，不受 Activity 生命周期影响。
     *
     * 可安全重复调用：如果 Service 已在运行，onStartCommand 会被再次调用（幂等）。
     */
    private fun startMonitorService() {
        val intent = Intent(this, MonitorForegroundService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Log.i(TAG, "MonitorForegroundService start requested")
    }

    // ══════════════════════════════════════════════════════════════
    // 传感器 → EventBus 管道
    // ══════════════════════════════════════════════════════════════

    /**
     * 注册传感器回调，将传感器数据依次通过各 detector，
     * 检测到的事件直接发射到 EventBus。
     *
     * 注意：SensorCallback.onSensorChanged 在传感器事件线程（后台）调用。
     * EventBus.tryEmit() 是线程安全的非阻塞调用，无需切到主线程。
     * UI 线程的更新由 [startEventCollection] 中的 EventBus collector 完成。
     */
    private fun startSensorPipeline() {
        sensorManager.registerCallback(object : SensorCallback {
            override fun onSensorChanged(data: AccelData) {
                Log.v(TAG, "sensor callback: mag=${"%.2f".format(data.magnitude)}")
                // 执行检测（后台线程，轻量数学）
                val impact = impactDetector.process(data)
                val shake = shakeDetector.process(data)
                val drop = dropDetector.process(data)

                // 检测到事件 → 直接发射到 EventBus（线程安全）
                if (impact is ImpactResult.Detected) {
                    Log.d(TAG, "Event: Impact (${"%.2f".format(impact.intensity)})")
                    EventBus.tryEmit(DeviceEvent.Impact(impact.intensity))
                }
                if (shake) {
                    Log.d(TAG, "Event: Shake")
                    EventBus.tryEmit(DeviceEvent.Shake)
                }
                if (drop is DropResult.Detected) {
                    Log.d(TAG, "Event: Drop (${"%.1f".format(drop.impactForce)})")
                    EventBus.tryEmit(DeviceEvent.Drop(drop.impactForce))
                }
            }

            override fun onAccuracyChanged(sensorType: Int, accuracy: Int) {
                Log.d(TAG, "Accuracy: type=$sensorType accuracy=$accuracy")
            }
        })
    }

    // ══════════════════════════════════════════════════════════════
    // EventBus → UI 收集
    // ══════════════════════════════════════════════════════════════

    /**
     * 从 EventBus 收集所有事件（传感器 + 设备状态），
     * 统一更新 UI 状态。
     *
     * 这是 UI 更新的唯一入口，避免了旧架构中传感器事件和
     * 设备事件分别更新 UI 导致的代码重复。
     */
    private var collectionJob: kotlinx.coroutines.Job? = null

    private fun startEventCollection() {
        collectionJob?.cancel()
        collectionJob = activityScope.launch {
            EventBus.events.collect { event ->
                Log.d(TAG, "UI collector received: ${event.toDisplayLabel()}")
                lastEvent.value = event
                addToLog(event)
            }
        }
    }

    /**
     * 将事件追加到日志列表（最新在前），超过上限则截断。
     */
    private fun addToLog(event: DeviceEvent) {
        eventLog.add(0, DeviceEventLogEntry(
            timestamp = System.currentTimeMillis(),
            event = event
        ))
        if (eventLog.size > maxEventLogSize) {
            eventLog.removeRange(maxEventLogSize, eventLog.size)
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 权限
    // ══════════════════════════════════════════════════════════════

    private fun requestNotificationPermission() {
        if (!PermissionManager.hasNotificationPermission(this)) {
            PermissionManager.requestNotificationPermission(this)
            Log.i(TAG, "Notification permission requested")
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
