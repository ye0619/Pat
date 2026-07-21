package com.example.pat.event

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 原子事件总线 —— 全局原子事件分发中心。
 *
 * 与 [EventBus] 平行运行：
 * ```
 * SensorDataBus  → AccelData (高频 raw data for UI)
 * EventBus       → DeviceEvent (语义事件 for 当前规则引擎)
 * AtomicEventBus → AtomicEvent (底层事实 for RuleEngineV2 组合规则)
 * ```
 *
 * 所有 Monitor/Detector 在产生 [DeviceEvent] 的同时，
 * 向此总线发射对应的 [AtomicEvent]。
 *
 * 基于 [SharedFlow]：
 * - 无黏性（replay = 0）
 * - buffer = 64（足够容纳高频传感器事件）
 *
 * 使用方式：
 * ```
 * // 发射
 * AtomicEventBus.tryEmit(AtomicEvent.Shake(System.currentTimeMillis()))
 *
 * // 收集
 * AtomicEventBus.events.collect { event -> ... }
 * ```
 */
object AtomicEventBus {

    private val _events = MutableSharedFlow<AtomicEvent>(
        replay = 0,
        extraBufferCapacity = 128  // v2: 增大缓冲区 — 统一总线承载所有事件
    )

    /** 公开的原子事件流 */
    val events: SharedFlow<AtomicEvent> = _events.asSharedFlow()

    /**
     * 挂起式发送事件（协程上下文）。
     */
    suspend fun emit(event: AtomicEvent) {
        _events.emit(event)
    }

    /**
     * 非挂起式发送事件（BroadcastReceiver / SensorCallback 等非协程上下文）。
     * @return 事件是否成功入队
     */
    fun tryEmit(event: AtomicEvent): Boolean {
        return _events.tryEmit(event)
    }
}
