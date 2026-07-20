package com.example.pat.event

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 事件总线 —— 全局动作事件分发中心。
 *
 * 基于 [SharedFlow] 实现：
 * - 无黏性事件（replay = 0），只分发当前及之后的事件
 * - 带缓冲（extraBufferCapacity = 16）应对高频传感器事件
 *
 * 使用方式：
 * ```
 * // 发送事件（非挂起版本，用于非协程上下文）
 * EventBus.tryEmit(MotionEvent.Impact(0.5f))
 *
 * // 收集事件
 * EventBus.events.collect { event -> handleEvent(event) }
 * ```
 *
 * 参考文档：6.3 事件分发机制
 */
object EventBus {

    private val _events = MutableSharedFlow<MotionEvent>(
        replay = 0,
        extraBufferCapacity = 16
    )

    /** 公开的事件流。behavior 层及上层订阅此 Flow。 */
    val events: SharedFlow<MotionEvent> = _events.asSharedFlow()

    /**
     * 挂起式发送事件。用于协程上下文。
     */
    suspend fun emit(event: MotionEvent) {
        _events.emit(event)
    }

    /**
     * 非挂起式发送事件。用于传感器回调线程等非协程上下文。
     * @return 事件是否成功入队
     */
    fun tryEmit(event: MotionEvent): Boolean {
        return _events.tryEmit(event)
    }
}
