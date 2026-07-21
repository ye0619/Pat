package com.example.pat.ui.navigation

import com.example.pat.event.EventType

/**
 * 应用内页面导航状态。
 */
sealed class Screen {

    /** 首页 */
    data object Home : Screen()

    /** 事件列表页 */
    data object EventList : Screen()

    /** 编辑事件规则（事件类型 + 阈值 + 预设选择） */
    data class EditEvent(val eventType: EventType) : Screen()

    /** 编辑/创建自定义预设（关联到某个事件类型） */
    data class EditPreset(
        val eventType: EventType,
        val presetId: String? = null  // null = 新建
    ) : Screen()
}
