package com.example.pat.model

import com.example.pat.event.EventType

/**
 * 事件规则 —— 用户对一个事件类型的完整配置。
 *
 * 每个事件类型最多一条规则。
 * 规则通过 [presetId] 引用 [ReactionPreset] 获取反馈内容。
 *
 * 与旧版 [com.example.pat.config.EventConfig] 的区别：
 * - 移除了 text / voicePath 字段
 * - 新增 presetId 字段，解耦事件规则与反馈内容
 *
 * @property id 唯一标识符
 * @property eventType 事件类型
 * @property enabled 是否启用此事件监听
 * @property threshold 触发阈值（SCREEN_LONG_USAGE=分钟, LOW_BATTERY=百分比, 其他=0）
 * @property presetId 关联的 ReactionPreset.id（空字符串表示无预设）
 * @property notificationEnabled 是否发送通知
 *
 * 参考：目标架构 - EventConfig 数据模型
 */
data class EventConfig(
    val id: String = "",
    val eventType: EventType,
    val enabled: Boolean = true,
    val threshold: Int = defaultThreshold(eventType),
    val presetId: String = "",
    val notificationEnabled: Boolean = true
) {
    companion object {

        /** 默认阈值 */
        fun defaultThreshold(type: EventType): Int = when (type) {
            EventType.SCREEN_LONG_USAGE -> 120  // 2小时
            EventType.LOW_BATTERY -> 20          // 20%
            EventType.CHARGE_START -> 0
            EventType.SHAKE -> 0
            EventType.IMPACT -> 0
        }

        /** 默认反馈文本（无预设时回退） */
        fun defaultText(type: EventType): String = when (type) {
            EventType.SCREEN_LONG_USAGE -> "别看了，我想睡觉了"
            EventType.CHARGE_START -> "谢谢给我补充能量"
            EventType.LOW_BATTERY -> "我要没电啦"
            EventType.SHAKE -> "别摇我"
            EventType.IMPACT -> "好痛！轻一点"
        }

        /** 事件类型的可读中文名 */
        fun displayName(type: EventType): String = when (type) {
            EventType.SCREEN_LONG_USAGE -> "长时间使用"
            EventType.CHARGE_START -> "开始充电"
            EventType.LOW_BATTERY -> "电量低"
            EventType.SHAKE -> "摇晃手机"
            EventType.IMPACT -> "撞击"
        }
    }
}
