package com.example.pat.preset

import com.example.pat.event.EventType

/**
 * 预设反馈数据模型 —— 内置的文本+语音反馈。
 *
 * 每个预设对应一个 assets/ 中的音频文件及其关联的文本内容。
 * 文件名格式：事件类型（文本内容）.wav
 *
 * @property id 唯一标识符
 * @property eventType 关联的事件类型
 * @property displayText 从文件名解析的显示文本（即括号内的内容）
 * @property audioAssetPath assets 中的音频文件路径
 *
 * 参考：Task 1 - 预设数据模型
 */
data class PresetReaction(
    val id: String,
    val eventType: EventType,
    val displayText: String,
    val audioAssetPath: String
)
