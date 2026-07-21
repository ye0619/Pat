package com.example.pat.model

import com.example.pat.event.EventType

/**
 * 音频类型 —— 区分内置预设和用户自定义。
 */
enum class AudioType {
    /** 内置预设（来自 assets/ 音频文件，文件名编码事件类型和文本） */
    PRESET,
    /** 用户自定义（用户上传的音频或修改文本后的预设） */
    CUSTOM
}

/**
 * 反馈预设 —— 一条完整的文本+语音反馈配置。
 *
 * 预设是 EventConfig 的反馈内容载体。
 * 一个事件规则通过 [presetId] 引用一个 ReactionPreset。
 *
 * 内置预设由 [PresetLoader] 从 assets/ 文件名自动生成。
 * 用户可从内置预设中选择，也可创建自定义预设。
 *
 * @property id 唯一标识符（内置预设由 PresetLoader 生成 UUID，自定义预设用户命名）
 * @property name 可读名称（如 "休息提醒"）
 * @property text 反馈文本内容
 * @property audioAssetPath assets 中的路径或用户文件路径
 * @property audioType 音频来源类型
 * @property eventType 关联的事件类型（null 表示通用预设）
 *
 * 参考：目标架构 - ReactionPreset 数据模型
 */
data class ReactionPreset(
    val id: String,
    val name: String,
    val text: String,
    val audioAssetPath: String = "",
    val audioType: AudioType = AudioType.PRESET,
    val eventType: EventType? = null
) {
    companion object {
        /** 根据显示文本生成默认名称 */
        fun nameFromText(text: String): String {
            return if (text.length <= 8) text else text.take(8) + "..."
        }
    }
}
