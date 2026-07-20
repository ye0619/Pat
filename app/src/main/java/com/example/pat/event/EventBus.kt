package com.example.pat.event

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 事件总线 —— 全局设备事件分发中心。
 *
 * 基于 [SharedFlow] 实现：
 * - 无黏性事件（replay = 0），只分发当前及之后的事件
 * - 带缓冲（extraBufferCapacity = 16）应对高频事件
 *
 * 所有 Monitor（Battery / Screen / Sensor / DeviceState）统一向此总线发射事件。
 * UI 层通过 collect 接收事件并更新界面。
 *
 * 使用方式：
 * ```
 * // 发送事件（非挂起版本，用于传感器回调和 BroadcastReceiver）
 * EventBus.tryEmit(DeviceEvent.ChargeStart)
 *
 * // 收集事件
 * EventBus.events.collect { event -> handleEvent(event) }
 * ```
 *
 * 参考文档：6.3 事件分发机制
 */
object EventBus {

    private val _events = MutableSharedFlow<DeviceEvent>(
        replay = 0,
        extraBufferCapacity = 16
    )

    /** 公开的事件流。UI 层及 personality 层订阅此 Flow。 */
    val events: SharedFlow<DeviceEvent> = _events.asSharedFlow()

    /**
     * 挂起式发送事件。用于协程上下文。
     */
    suspend fun emit(event: DeviceEvent) {
        _events.emit(event)
    }

    /**
     * 非挂起式发送事件。用于 BroadcastReceiver 等非协程上下文。
     * @return 事件是否成功入队
     */
    fun tryEmit(event: DeviceEvent): Boolean {
        return _events.tryEmit(event)
    }
}
