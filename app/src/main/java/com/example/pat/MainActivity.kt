package com.example.pat

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import com.example.pat.event.DeviceEvent
import com.example.pat.event.DeviceEventLogEntry
import com.example.pat.event.EventBus
import com.example.pat.event.SensorDataBus
import com.example.pat.event.toDisplayLabel
import com.example.pat.service.MonitorForegroundService
import com.example.pat.ui.SensorDebugScreen
import com.example.pat.ui.theme.PatTheme
import com.example.pat.util.PermissionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 主 Activity —— 纯 UI 层。
 *
 * 不持有任何传感器、检测器或监控器实例。
 * 所有数据通过全局总线获取：
 * ```
 * SensorDataBus → 传感器原始数据（X, Y, Z） + 运行状态
 * EventBus      → 所有 DeviceEvent（Shake, ScreenWake, ChargeStart…）
 * ```
 *
 * 生命周期：
 * ```
 * onCreate → 启动 ForegroundService + 开始传感器帧计数
 * onStart  → 开始 EventBus 收集（更新 UI 事件日志）
 * onStop   → 停止 EventBus 收集（省电，Service 继续后台运行）
 * onDestroy→ 取消协程
 * ```
 */
class MainActivity : ComponentActivity() {

    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // ── UI 状态 ──
    private val lastEvent = mutableStateOf<DeviceEvent?>(null)
    private val eventLog = mutableStateListOf<DeviceEventLogEntry>()
    private val maxEventLogSize = 50
    private val sensorFrameCount = mutableStateOf(0)

    // ── EventBus 收集 ──
    private var collectionJob: kotlinx.coroutines.Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 请求通知权限（前台服务必需）
        requestNotificationPermission()

        // 启动 ForegroundService（托管所有监控器 + 传感器管道）
        startMonitorService()

        // 传感器帧计数器（用于 UI 诊断显示）
        activityScope.launch {
            SensorDataBus.sensorData.collect {
                sensorFrameCount.value = (sensorFrameCount.value + 1) % 1000
            }
        }

        setContent {
            // 在 Composable 作用域内收集 StateFlow
            val isSensorRunning by SensorDataBus.isRunning.collectAsState()

            PatTheme {
                SensorDebugScreen(
                    isSensorRunning = isSensorRunning,
                    sensorDataFlow = SensorDataBus.sensorData,
                    latestData = SensorDataBus.latestData,
                    lastEvent = lastEvent.value,
                    sensorFrameCount = sensorFrameCount.value,
                    eventLog = eventLog.toList(),
                    onStartClick = {
                        // 通过 Intent 命令让 Service 启动传感器
                        sendServiceCommand(MonitorForegroundService.ACTION_START_SENSOR)
                    },
                    onStopClick = {
                        sendServiceCommand(MonitorForegroundService.ACTION_STOP_SENSOR)
                    },
                    onTestEventClick = {
                        Log.i(TAG, "=== Test button pressed ===")
                        EventBus.tryEmit(DeviceEvent.Shake)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Log.i(TAG, "MainActivity created — UI only, all monitors in Service")
    }

    override fun onStart() {
        super.onStart()
        startEventCollection()
        Log.i(TAG, "onStart: EventBus collection started")
    }

    override fun onStop() {
        collectionJob?.cancel()
        collectionJob = null
        Log.i(TAG, "onStop: EventBus collection stopped (Service continues)")
        super.onStop()
    }

    override fun onDestroy() {
        collectionJob?.cancel()
        activityScope.cancel()
        Log.i(TAG, "onDestroy")
        super.onDestroy()
    }

    // ══════════════════════════════════════════════════════════════
    // Service 通信
    // ══════════════════════════════════════════════════════════════

    /**
     * 启动 MonitorForegroundService。
     * Service 内部托管所有监控器 + 传感器，独立于 Activity 生命周期。
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

    /**
     * 向 Service 发送命令（如手动启停传感器）。
     */
    private fun sendServiceCommand(action: String) {
        val intent = Intent(this, MonitorForegroundService::class.java).apply {
            this.action = action
        }
        startService(intent)
        Log.i(TAG, "Service command sent: $action")
    }

    // ══════════════════════════════════════════════════════════════
    // EventBus → UI
    // ══════════════════════════════════════════════════════════════

    /**
     * 从 EventBus 收集所有事件，统一更新 UI。
     * 这是 UI 事件更新的唯一入口。
     */
    private fun startEventCollection() {
        collectionJob?.cancel()
        collectionJob = activityScope.launch {
            EventBus.events.collect { event ->
                Log.d(TAG, "UI received: ${event.toDisplayLabel()}")
                lastEvent.value = event
                addToLog(event)
            }
        }
    }

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
