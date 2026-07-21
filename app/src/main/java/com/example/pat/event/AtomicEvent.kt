package com.example.pat.event

/**
 * 原子事件 —— 系统能检测到的最小设备行为单元。
 *
 * 与 [DeviceEvent] 的区别：
 * - DeviceEvent 是高层语义事件（携带业务含义，如 LowBattery）
 * - AtomicEvent 是底层事实记录（"发生了什么"，不携带业务判断）
 *
 * 所有 Monitor/Detector 在产生 DeviceEvent 的同时，
 * 向 [AtomicEventBus] 发射对应的 AtomicEvent，
 * 供 [com.example.pat.engine.RuleEngineV2] 进行组合条件匹配。
 *
 * @property timestamp 事件发生时间戳（毫秒，System.currentTimeMillis()）
 */
sealed class AtomicEvent {
    abstract val timestamp: Long

    // ══════════════════════════════════════════════════════════════
    // 动作事件（加速度计 → Detector）
    // ══════════════════════════════════════════════════════════════

    /** 摇晃 */
    data class Shake(override val timestamp: Long) : AtomicEvent()

    /** 撞击/拍击 */
    data class Impact(
        override val timestamp: Long,
        val intensity: Float
    ) : AtomicEvent()

    /** 跌落 */
    data class Drop(
        override val timestamp: Long,
        val impactForce: Float
    ) : AtomicEvent()

    // ══════════════════════════════════════════════════════════════
    // 屏幕事件（ScreenMonitor）
    // ══════════════════════════════════════════════════════════════

    /** 屏幕点亮 */
    data class ScreenOn(override val timestamp: Long) : AtomicEvent()

    /** 屏幕关闭 */
    data class ScreenOff(override val timestamp: Long) : AtomicEvent()

    // ══════════════════════════════════════════════════════════════
    // 电池事件（BatteryMonitor）
    // ══════════════════════════════════════════════════════════════

    /** 开始充电 */
    data class ChargeStart(override val timestamp: Long) : AtomicEvent()

    /** 停止充电 */
    data class ChargeStop(override val timestamp: Long) : AtomicEvent()

    /** 电量百分比变化 */
    data class BatteryLevel(
        override val timestamp: Long,
        val percent: Int
    ) : AtomicEvent()

    // ══════════════════════════════════════════════════════════════
    // 累计事件（ScreenMonitor 衍生）
    // ══════════════════════════════════════════════════════════════

    /** 累计亮屏超过阈值 */
    data class LongUsage(
        override val timestamp: Long,
        val minutes: Int
    ) : AtomicEvent()

    /** 深夜使用 */
    data class LateNight(override val timestamp: Long) : AtomicEvent()
}

/**
 * 将 [AtomicEvent] 映射到其类型枚举。
 */
fun AtomicEvent.toType(): AtomicEventType = when (this) {
    is AtomicEvent.Shake -> AtomicEventType.SHAKE
    is AtomicEvent.Impact -> AtomicEventType.IMPACT
    is AtomicEvent.Drop -> AtomicEventType.DROP
    is AtomicEvent.ScreenOn -> AtomicEventType.SCREEN_ON
    is AtomicEvent.ScreenOff -> AtomicEventType.SCREEN_OFF
    is AtomicEvent.ChargeStart -> AtomicEventType.CHARGE_START
    is AtomicEvent.ChargeStop -> AtomicEventType.CHARGE_STOP
    is AtomicEvent.BatteryLevel -> AtomicEventType.BATTERY_LEVEL
    is AtomicEvent.LongUsage -> AtomicEventType.LONG_USAGE
    is AtomicEvent.LateNight -> AtomicEventType.LATE_NIGHT
}
