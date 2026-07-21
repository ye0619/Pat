package com.example.pat.model

import com.example.pat.event.EventType

/**
 * 事件规则 —— 用户对一个事件类型的完整配置。
 *
 * 每个事件类型最多一条规则。
 * 规则通过 [presetId] 引用 [ReactionPreset] 获取反馈内容。
 *
 * @property id 唯一标识符
 * @property eventType 事件类型
 * @property enabled 是否启用此事件监听
 * @property threshold 触发阈值（SCREEN_LONG_USAGE=分钟, LOW_BATTERY=百分比, 其他=0）
 * @property presetId 关联的 ReactionPreset.id（空字符串表示无预设）
 * @property notificationEnabled 是否发送通知
 * @property minIntervalMinutes 最小触发间隔（分钟）。同一事件在此时间内不会重复触发。
 *                              设为 0 表示无限制。默认为 10 分钟。
 *                              当某一事件正在播放音频时，其他事件也会被阻止。
 */
data class EventConfig(
    val id: String = "",
    val eventType: EventType,
    val enabled: Boolean = true,
    val threshold: Int = defaultThreshold(eventType),
    val presetId: String = "",
    val notificationEnabled: Boolean = true,
    val minIntervalMinutes: Int = 10,
    /** 通知时是否震动（默认关闭） */
    val vibrationEnabled: Boolean = false,
    /** 通知时是否播放系统通知音效（默认关闭） */
    val soundEnabled: Boolean = false,
    /** 是否以 Heads-up 横幅显示（关闭则仅静默出现在通知栏） */
    val showHeadsUp: Boolean = true,
    /** 锁屏时是否显示通知内容（关闭则锁屏仅显示图标） */
    val lockScreenPublic: Boolean = true,
    /** 用户自定义反馈文本（覆盖预设，空则使用预设文本） */
    val customText: String = "",
    /** 用户自定义反馈音频路径（覆盖预设，空则使用预设音频） */
    val customAudioPath: String = ""
) {
    /** 有效反馈文本：用户自定义 > 预设 > 默认 */
    fun effectiveText(preset: ReactionPreset?): String =
        customText.ifBlank { preset?.text ?: defaultText(eventType) }

    /** 有效音频路径：用户自定义 > 预设 */
    fun effectiveAudioPath(preset: ReactionPreset?): String =
        customAudioPath.ifBlank { preset?.audioAssetPath ?: "" }
    companion object {

        /** 默认阈值 */
        fun defaultThreshold(type: EventType): Int = when (type) {
            EventType.SCREEN_LONG_USAGE -> 120
            EventType.LOW_BATTERY -> 20
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
            EventType.IMPACT -> "好痛！轻一点拍"
        }

        /** 事件类型的可读中文名 */
        fun displayName(type: EventType): String = when (type) {
            EventType.SCREEN_LONG_USAGE -> "长时间使用"
            EventType.CHARGE_START -> "开始充电"
            EventType.LOW_BATTERY -> "电量低"
            EventType.SHAKE -> "摇晃手机"
            EventType.IMPACT -> "拍击"
        }
    }
}
