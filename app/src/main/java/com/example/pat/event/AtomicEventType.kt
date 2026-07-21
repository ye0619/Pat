package com.example.pat.event

/**
 * 原子事件类型枚举 —— 对应 [AtomicEvent] 的每一个子类。
 *
 * 用于：
 * - 规则条件匹配（[ConditionClause.eventType]）
 * - UI 展示（事件选择器、条件列表）
 * - [EventHistoryBuffer] 按类型索引
 *
 * @property displayName 中文名称
 * @property description 事件描述
 * @property hasValue 是否携带数值（如电量%、使用分钟数），用于阈值条件
 */
enum class AtomicEventType(
    val displayName: String,
    val description: String,
    val hasValue: Boolean = false
) {
    SHAKE("摇晃", "加速度计检测到设备摇晃"),
    IMPACT("撞击", "加速度计检测到瞬时高加速度冲击"),
    DROP("跌落", "设备经历自由落体后撞击"),
    SCREEN_ON("屏幕亮起", "屏幕被点亮或解锁"),
    SCREEN_OFF("屏幕关闭", "屏幕被关闭"),
    CHARGE_START("开始充电", "连接了充电器"),
    CHARGE_STOP("停止充电", "断开了充电器"),
    BATTERY_LEVEL("电量变化", "电量百分比发生变化", hasValue = true),
    LONG_USAGE("长时间使用", "累计亮屏时间超过阈值", hasValue = true),
    LATE_NIGHT("深夜使用", "在深夜时段（23:00-05:00）使用手机")
}
