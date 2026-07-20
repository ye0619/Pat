package com.example.pat

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
import com.example.pat.monitor.DeviceStateMonitor
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
 * - 管理 MotionSensorManager + DeviceStateMonitor 生命周期
 * - 连接传感器 detector → DeviceEvent 映射
 * - 收集所有 DeviceEvent 更新 UI 事件日志
 * - 处理运行时权限
 *
 * 生命周期设计：
 * ```
 * onStart  → startListening() + DeviceStateMonitor.start()
 * onStop   → DeviceStateMonitor.stop() + stopListening()
 * onDestroy→ release() + scope.cancel()
 * ```
 *
 * 数据流：
 * ```
 * Sensor → detectors → callback → DeviceEvent → EventBus + UI
 * DeviceStateMonitor.events → EventBus + UI
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

    // ── 系统状态监控（复合：Battery + Screen + DeviceState 的整合入口） ──
    private lateinit var deviceStateMonitor: DeviceStateMonitor
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // ── UI 状态 ──
    private val lastEvent = mutableStateOf<DeviceEvent?>(null)
    private val eventLog = mutableStateListOf<DeviceEventLogEntry>()
    private val maxEventLogSize = 50

    /** UI 使用的传感器数据流（已降频，避免 GPU 过载） */
    private lateinit var uiSensorDataFlow: Flow<AccelData>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 初始化传感器管理器
        sensorManager = MotionSensorManager(this)

        // 降频传感器数据流用于 UI 显示（10fps，避免 GPU BufferQueue 过载）
        @OptIn(FlowPreview::class)
        val sampled = sensorManager.sensorData.sample(100)
        uiSensorDataFlow = sampled

        // 初始化系统状态监控（复合型：内部包含 Battery + Screen 监控器）
        deviceStateMonitor = DeviceStateMonitor(applicationContext, activityScope)

        // 请求运行时权限
        requestNotificationPermission()

        // 建立传感器检测管道
        startSensorPipeline()

        setContent {
            PatTheme {
                SensorDebugScreen(
                    isSensorRunning = sensorManager.isRunning,
                    sensorDataFlow = uiSensorDataFlow,
                    latestData = sensorManager.latestData,
                    lastEvent = lastEvent.value,
                    eventLog = eventLog.toList(),
                    onStartClick = { sensorManager.startListening() },
                    onStopClick = { sensorManager.stopListening() },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Log.i(TAG, "MainActivity created")
    }

    override fun onStart() {
        super.onStart()
        // 可见：启动传感器 + 系统监控
        sensorManager.startListening()
        deviceStateMonitor.start()
        startEventCollection()
        Log.i(TAG, "onStart: all monitors started")
    }

    override fun onStop() {
        // 不可见：停止系统监控 + 传感器（省电）
        deviceStateMonitor.stop()
        sensorManager.stopListening()
        Log.i(TAG, "onStop: all monitors stopped")
        super.onStop()
    }

    override fun onDestroy() {
        activityScope.cancel()
        if (::sensorManager.isInitialized) {
            sensorManager.release()
        }
        Log.i(TAG, "onDestroy: resources released")
        super.onDestroy()
    }

    // ══════════════════════════════════════════════════════════════
    // 传感器 → 事件 管道
    // ══════════════════════════════════════════════════════════════

    /**
     * 注册 SensorCallback，将传感器数据依次通过各 detector
     * 并将检测到的事件发射到 EventBus 和 UI。
     */
    private fun startSensorPipeline() {
        sensorManager.registerCallback(object : SensorCallback {
            override fun onSensorChanged(data: AccelData) {
                processImpact(data)
                processShake(data)
                processDrop(data)
            }

            override fun onAccuracyChanged(sensorType: Int, accuracy: Int) {
                Log.d(TAG, "Accuracy: type=$sensorType accuracy=$accuracy")
            }
        })
    }

    private fun processImpact(data: AccelData) {
        when (val result = impactDetector.process(data)) {
            is ImpactResult.Detected -> {
                val event = DeviceEvent.Impact(result.intensity)
                emitEvent(event)
                Log.d(TAG, "Event: Impact (${"%.2f".format(result.intensity)})")
            }
            is ImpactResult.None -> { /* 无事件 */ }
        }
    }

    private fun processShake(data: AccelData) {
        if (shakeDetector.process(data)) {
            emitEvent(DeviceEvent.Shake)
            Log.d(TAG, "Event: Shake")
        }
    }

    private fun processDrop(data: AccelData) {
        when (val result = dropDetector.process(data)) {
            is DropResult.Detected -> {
                val event = DeviceEvent.Drop(result.impactForce)
                emitEvent(event)
                Log.d(TAG, "Event: Drop (${"%.1f".format(result.impactForce)})")
            }
            is DropResult.None -> { /* 无事件 */ }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 事件收集与 UI 更新
    // ══════════════════════════════════════════════════════════════

    /**
     * 收集 [DeviceStateMonitor.events]（电池/屏幕/衍生事件），
     * 转发到 EventBus 并更新 UI。
     */
    private fun startEventCollection() {
        activityScope.launch {
            deviceStateMonitor.events.collect { event ->
                EventBus.tryEmit(event)
                addToLog(event)
            }
        }
    }

    /**
     * 发射一个事件：更新状态值、记录日志、转发到 EventBus。
     */
    private fun emitEvent(event: DeviceEvent) {
        lastEvent.value = event
        addToLog(event)
        EventBus.tryEmit(event)
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
