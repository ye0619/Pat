package com.example.pat.config

import com.example.pat.event.EventType

/**
 * 事件配置数据模型 —— 用户对单个事件类型的完整配置。
 *
 * 每个事件类型拥有一份配置，保存在 SharedPreferences 中。
 * 用户在编辑页面修改配置后，[PreferenceManager] 负责持久化。
 *
 * @property id 唯一标识符（UUID）
 * @property eventType 事件类型
 * @property enabled 是否启用此事件的监听
 * @property threshold 阈值：SCREEN_LONG_USAGE = 分钟数, LOW_BATTERY = 电量百分比
 * @property text 反馈文本（通知内容）
 * @property voicePath 用户上传的音频文件路径（空表示无语音）
 * @property notificationEnabled 是否发送通知
 *
 * 参考文档：9. 数据结构示例
 */
data class EventConfig(
    val id: String = "",
    val eventType: EventType,
    val enabled: Boolean = true,
    val threshold: Int = defaultThreshold(eventType),
    val text: String = defaultText(eventType),
    val voicePath: String = "",
    val notificationEnabled: Boolean = true
) {
    companion object {

        /** 默认阈值 */
        fun defaultThreshold(type: EventType): Int = when (type) {
            EventType.SCREEN_LONG_USAGE -> 120  // 2小时
            EventType.LOW_BATTERY -> 20          // 20%
            EventType.CHARGE_START -> 0
            EventType.SHAKE -> 0
        }

        /** 默认反馈文本 */
        fun defaultText(type: EventType): String = when (type) {
            EventType.SCREEN_LONG_USAGE -> "别看了，我想睡觉了"
            EventType.CHARGE_START -> "谢谢给我补充能量"
            EventType.LOW_BATTERY -> "我要没电啦"
            EventType.SHAKE -> "别摇我"
        }

        /** 事件类型的可读中文名 */
        fun displayName(type: EventType): String = when (type) {
            EventType.SCREEN_LONG_USAGE -> "屏幕使用过久"
            EventType.CHARGE_START -> "开始充电"
            EventType.LOW_BATTERY -> "电量低"
            EventType.SHAKE -> "摇晃手机"
        }
    }
}
