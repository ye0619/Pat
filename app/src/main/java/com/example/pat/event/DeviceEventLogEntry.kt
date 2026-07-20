package com.example.pat.event

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 事件日志条目。
 *
 * 用于 UI 层展示事件历史记录。
 * 包含时间戳、可读标签和原始事件引用。
 */
data class DeviceEventLogEntry(
    val timestamp: Long,
    val event: DeviceEvent
) {
    /** 格式化的时间戳：HH:mm:ss */
    val formattedTime: String
        get() = TIMESTAMP_FORMAT.format(Date(timestamp))

    /** 事件的简短可读标签 */
    val displayLabel: String
        get() = event.toDisplayLabel()

    companion object {
        private val TIMESTAMP_FORMAT = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    }
}

/**
 * 将 [DeviceEvent] 映射为简短的可读标签。
 */
fun DeviceEvent.toDisplayLabel(): String = when (this) {
    is DeviceEvent.Shake -> "Shake"
    is DeviceEvent.Impact -> "Impact (${"%.2f".format(intensity)})"
    is DeviceEvent.Drop -> "Drop (${"%.1f".format(impactForce)})"
    is DeviceEvent.ChargeStart -> "Charge Start"
    is DeviceEvent.LowBattery -> "Low Battery"
    is DeviceEvent.BatteryFull -> "Battery Full"
    is DeviceEvent.ScreenWake -> "Screen Wake"
    is DeviceEvent.LongUsage -> "Long Usage (${minutes}min)"
    is DeviceEvent.LateNight -> "Late Night"
}
