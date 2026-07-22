package com.example.pat.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.pat.event.AtomicEvent
import com.example.pat.event.AtomicEventBus

/**
 * 应用内点击/长按追踪器。无视觉反馈，仅发射事件。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClickTracker(content: @Composable () -> Unit) {
    Box(Modifier.combinedClickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,  // 无涟漪效果
        onClick = { AtomicEventBus.tryEmit(AtomicEvent.Click(System.currentTimeMillis())) },
        onLongClick = { AtomicEventBus.tryEmit(AtomicEvent.LongPress(System.currentTimeMillis())) }
    )) { content() }
}
