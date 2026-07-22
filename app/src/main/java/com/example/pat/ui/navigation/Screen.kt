package com.example.pat.ui.navigation

import com.example.pat.event.EventType

sealed class Screen {
    data object Home : Screen()
    data object EventList : Screen()
    data class EditEvent(val eventType: EventType) : Screen()
    data class EditPreset(val eventType: EventType, val presetId: String? = null) : Screen()
    data class NewEvent(val defId: String? = null) : Screen()
}
