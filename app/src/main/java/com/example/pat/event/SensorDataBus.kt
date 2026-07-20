package com.example.pat.event

import com.example.pat.sensor.AccelData
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 传感器数据全局总线。
 *
 * 与 [EventBus] 对称的设计：Service 写入传感器数据，
 * Activity / UI 层通过 [sensorData] Flow 收集。
 *
 * 分离传感器原始数据与设备事件的通道：
 * ```
 * SensorDataBus  → AccelData (高频 raw data for UI)
 * EventBus       → DeviceEvent (语义事件 for behavior)
 * ```
 *
 * 使用方式：
 * ```
 * // Service 写入
 * SensorDataBus.tryEmit(accelData)
 * SensorDataBus.setRunning(true)
 *
 * // Activity 读取
 * SensorDataBus.sensorData.collect { ... }
 * val running by SensorDataBus.isRunning.collectAsState()
 * ```
 */
object SensorDataBus {

    private val _sensorData = MutableSharedFlow<AccelData>(
        replay = 1,   // 新 collector 立即获取最新一帧
        extraBufferCapacity = 64
    )
    /** 传感器原始数据流（replay=1，新订阅者立即获得最新值） */
    val sensorData: SharedFlow<AccelData> = _sensorData.asSharedFlow()

    private val _isRunning = MutableStateFlow(false)
    /** 传感器运行状态（供 Compose collectAsState 使用） */
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    /** 最新传感器数据（非协程消费者可直接读取） */
    @Volatile
    var latestData: AccelData = AccelData(0f, 0f, 0f, 0L)
        private set

    /**
     * 写入传感器数据（非挂起，线程安全）。
     * @return 数据是否成功入队
     */
    fun tryEmit(data: AccelData): Boolean {
        latestData = data
        return _sensorData.tryEmit(data)
    }

    /**
     * 更新传感器运行状态。
     */
    fun setRunning(running: Boolean) {
        _isRunning.value = running
    }
}
