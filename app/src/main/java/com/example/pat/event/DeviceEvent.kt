package com.example.pat.event

/**
 * 设备事件密封类 —— 系统的统一事件类型。
 *
 * 涵盖三类事件源：
 * - 动作事件（来自 MotionSensorManager + detectors）
 * - 设备状态事件（来自 BatteryMonitor）
 * - 用户行为事件（来自 ScreenMonitor + DeviceStateMonitor）
 *
 * 设计原则：
 * - 所有事件在 [EventBus] 中统一流转
 * - 每个事件携带语义必要参数，不含业务逻辑
 * - 新增事件类型只需在此添加子类，不影响现有代码
 *
 * 参考文档：6.2 事件模型
 */
sealed class DeviceEvent {

    // ───────── 核心动作事件 ─────────

    /** 摇晃事件 */
    data object Shake : DeviceEvent()

    /**
     * 撞击/拍击事件。
     * @param intensity 归一化强度 [0f, 1f]
     */
    data class Impact(val intensity: Float) : DeviceEvent()

    /**
     * 跌落事件。
     * @param impactForce 撞击原始峰值 (m/s²)
     */
    data class Drop(val impactForce: Float) : DeviceEvent()

    // ───────── 设备状态事件 ─────────

    /** 开始充电 */
    data object ChargeStart : DeviceEvent()

    /** 电量低（系统阈值，通常 ~15%） */
    data object LowBattery : DeviceEvent()

    /** 充电完成（电量 100%） */
    data object BatteryFull : DeviceEvent()

    // ───────── 用户行为事件 ─────────

    /** 点亮屏幕 / 解锁 */
    data object ScreenWake : DeviceEvent()

    /**
     * 长时间使用手机。
     * @param minutes 累计亮屏分钟数
     */
    data class LongUsage(val minutes: Int) : DeviceEvent()

    /** 深夜使用（23:00–05:00） */
    data object LateNight : DeviceEvent()
}
