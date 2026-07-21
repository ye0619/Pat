package com.example.pat.model

/**
 * 单个反馈项 —— 一条文本 + 一个音频路径。
 *
 * 每个事件规则可以拥有多个 ReactionItem，触发时随机选择一个。
 * 这实现了"反馈池"：同一事件每次触发可以有不同的文本/音频组合。
 *
 * @property text 反馈文本内容（空字符串 = 无文本）
 * @property audioPath 音频文件路径（空字符串 = 无音频）
 */
data class ReactionItem(
    val text: String = "",
    val audioPath: String = ""
) {
    companion object {
        /** 从旧版单独字段构造单个 ReactionItem（向后兼容） */
        fun fromLegacy(text: String, audioPath: String): ReactionItem {
            return ReactionItem(text = text, audioPath = audioPath)
        }
    }
}
