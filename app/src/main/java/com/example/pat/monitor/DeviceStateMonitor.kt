package com.example.pat.monitor

import android.content.Context
import android.util.Log
import com.example.pat.event.DeviceEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 设备状态监控器 —— 复合型 Monitor。
 *
 * 内部管理 [BatteryMonitor] 和 [ScreenMonitor] 的完整生命周期，
 * 将两个 Monitor 的事件流合并为统一的 [events] 输出。
 *
 * 额外职责：
 * - 定期检查 [ScreenMonitor.checkLongUsage]（每 5 分钟）
 * - 提供单一 [start] / [stop] 入口，简化 MainActivity 管理
 *
 * 使用方式：
 * ```
 * val monitor = DeviceStateMonitor(context, scope)
 * monitor.start()  // 同时启动 BatteryMonitor + ScreenMonitor + 定时检查
 * // ... collect monitor.events ...
 * monitor.stop()   // 同时停止所有内部 Monitor
 * ```
 *
 * 参考文档：5.5 设备状态监控设计
 */
class DeviceStateMonitor(
    context: Context,
    private val scope: CoroutineScope,
    lowBatteryPercent: Int = 20,
    private val longUsageMinutes: Int = 120
) : Monitor<DeviceEvent> {

    internal val batteryMonitor = BatteryMonitor(context, lowBatteryPercent)
    internal val screenMonitor = ScreenMonitor(context)

    private val _events = MutableSharedFlow<DeviceEvent>(
        replay = 0,
        extraBufferCapacity = 8
    )
    override val events: SharedFlow<DeviceEvent> = _events.asSharedFlow()

    private var isStarted = false
    private var mergeJob: Job? = null
    private var longUsageCheckJob: Job? = null

    override fun start() {
        if (isStarted) return
        isStarted = true

        // 1. 启动子 Monitor
        batteryMonitor.start()
        screenMonitor.start()

        // 2. 合并电池和屏幕事件流 → 转发到 _events
        mergeJob = scope.launch {
            merge(batteryMonitor.events, screenMonitor.events)
                .collect { event ->
                    _events.tryEmit(event)
                }
        }

        // 3. 定期检查长时间使用（每 5 分钟）
        longUsageCheckJob = scope.launch {
            while (isActive) {
                delay(5 * 60 * 1000L)
                screenMonitor.checkLongUsage(longUsageMinutes)
            }
        }

        Log.i(TAG, "DeviceStateMonitor started")
    }

    override fun stop() {
        if (!isStarted) return
        isStarted = false

        // 1. 停止前最后检查一次 LONG_USAGE
        screenMonitor.checkLongUsage()

        // 2. 取消合并协程 + 定时检查
        mergeJob?.cancel(); mergeJob = null
        longUsageCheckJob?.cancel(); longUsageCheckJob = null

        // 3. 停止子 Monitor
        batteryMonitor.stop()
        screenMonitor.stop()

        Log.i(TAG, "DeviceStateMonitor stopped")
    }

    companion object {
        private const val TAG = "DeviceStateMonitor"
    }
}
