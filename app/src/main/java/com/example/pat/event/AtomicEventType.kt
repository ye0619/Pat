package com.example.pat.event

/**
 * 原子事件类型枚举 —— 对应 [AtomicEvent] 的每一个子类。
 *
 * @property displayName 中文名称
 * @property description 事件描述
 * @property hasValue 是否携带数值（如电量%、使用分钟数）
 * @property supportsCount 是否支持次数条件（状态事件如 SCREEN_ON 不支持）
 * @property supportsTimeRange 是否支持时间段条件（如深夜使用可配置 22:00-06:00）
 */
enum class AtomicEventType(
    val displayName: String,
    val description: String,
    val hasValue: Boolean = false,
    val supportsCount: Boolean = true,
    val supportsTimeRange: Boolean = false,
    /** 是否需要无障碍服务（默认 false） */
    val requiresAccessibility: Boolean = false
) {
    SHAKE("摇晃手机", "加速度计检测到设备摇晃"),
    IMPACT("拍击手机", "用手指敲击手机背部/屏幕"),
    DROP("跌落", "设备经历自由落体后撞击"),
    SCREEN_ON("屏幕亮起", "屏幕被点亮或解锁", supportsCount = false),
    SCREEN_OFF("屏幕关闭", "屏幕被关闭", supportsCount = false),
    CHARGE_START("开始充电", "连接了充电器", supportsCount = false),
    CHARGE_STOP("停止充电", "断开了充电器", supportsCount = false),
    BATTERY_LEVEL("电量变化", "电量百分比发生变化", hasValue = true, supportsCount = false),
    LONG_USAGE("长时间使用", "累计亮屏时间超过阈值", hasValue = true, supportsCount = false),
    LATE_NIGHT("时间段使用", "在指定时间段内使用手机", supportsCount = false, supportsTimeRange = true),
    CLICK("点击屏幕 ⚠️", "检测屏幕点击（需开启无障碍服务）", supportsCount = true, requiresAccessibility = true),
    LONG_PRESS("长按屏幕 ⚠️", "检测屏幕长按（需开启无障碍服务）", supportsCount = false, requiresAccessibility = true)
}
