package com.example.pat.event

/**
 * 事件类型枚举 —— 用户可配置的事件类别。
 *
 * 每个枚举值对应一个可触发反馈的设备事件。
 * 用户可在配置界面中为每个事件类型设置阈值、反馈文本、语音等参数。
 *
 * 与 [DeviceEvent] 密封类的关系：
 * [DeviceEvent] 是系统运行时产生的事件实例（携带参数），
 * [EventType] 是事件的分类标签（用于配置匹配）。
 */
enum class EventType {

    /** 屏幕使用过久 —— 连续亮屏超过阈值 */
    SCREEN_LONG_USAGE,

    /** 开始充电 —— 插入充电器 */
    CHARGE_START,

    /** 电量低 —— 电量低于阈值百分比 */
    LOW_BATTERY,

    /** 摇晃手机 —— 加速度计检测到摇晃 */
    SHAKE,

    /** 坠落 —— 检测到短暂失重后发生强烈冲击 */
    DROP
}
