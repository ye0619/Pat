package com.example.pat.ui.navigation

import com.example.pat.event.EventType

/**
 * 应用内页面导航状态。
 *
 * 使用简单的 sealed class 管理页面栈，
 * 无需引入 Navigation Compose 库。
 */
sealed class Screen {

    /** 首页 —— 运行状态 + 今日统计 */
    data object Home : Screen()

    /** 事件列表页 —— 所有可配置事件 */
    data object EventList : Screen()

    /** 编辑页 —— 编辑单个事件的配置 */
    data class EditEvent(val eventType: EventType) : Screen()

    /** 预设测试页 —— 查看和试听所有内置预设 */
    data object PresetTest : Screen()
}
