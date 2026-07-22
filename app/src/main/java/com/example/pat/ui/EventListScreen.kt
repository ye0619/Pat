package com.example.pat.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pat.data.PresetRepository
import com.example.pat.event.EventType
import com.example.pat.model.EventConfig
import com.example.pat.model.EventDefinition
import com.example.pat.ui.components.EventCard

/**
 * 统一事件管理列表 —— 预设事件 + 自定义事件混合显示。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventListScreen(
    configs: List<EventConfig>,
    customDefs: List<EventDefinition>,
    presetRepository: PresetRepository?,
    conflictDefIds: Set<String> = emptySet(),
    onToggleConfig: (EventConfig) -> Unit,
    onToggleDef: (EventDefinition) -> Unit,
    onEditConfig: (EventConfig) -> Unit,
    onEditDef: (EventDefinition) -> Unit,
    onDeleteDef: (EventDefinition) -> Unit,
    onCreateClick: () -> Unit,
    onRestoreDefaults: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("事件管理", fontWeight = FontWeight.Bold) },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
                actions = {
                    var menuExpanded by remember { mutableStateOf(false) }
                    TextButton(onClick = onCreateClick) {
                        Text("新建", color = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { menuExpanded = true }) {
                        Text("⋮", style = MaterialTheme.typography.titleMedium)
                    }
                    DropdownMenu(menuExpanded, { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("恢复默认") },
                            onClick = { menuExpanded = false; onRestoreDefaults() }
                        )
                    }
                }
            )
        },
        modifier = modifier.fillMaxSize()
    ) { padding ->
        if (configs.isEmpty() && customDefs.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("暂无事件", style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                // 预设事件
                items(configs.size) { idx ->
                    val config = configs[idx]
                    val preset = config.presetId.let { id ->
                        if (id.isNotBlank()) presetRepository?.getById(id) else null
                    }
                    val feedback = if (config.reactions.isNotEmpty())
                        config.reactions.joinToString(" | ") { it.text }
                    else preset?.text ?: config.customText.ifBlank { EventConfig.defaultText(config.eventType) }

                    EventCard(
                        name = EventConfig.displayName(config.eventType),
                        subtitle = triggerSummary(config),
                        feedback = feedback,
                        enabled = config.enabled,
                        onToggle = { onToggleConfig(config.copy(enabled = it)) },
                        onEdit = { onEditConfig(config) }
                    )
                }
                // 自定义事件
                items(customDefs.size) { idx ->
                    val def = customDefs[idx]
                    val feedback = def.reactions.joinToString(" | ") { it.text }
                    EventCard(
                        name = def.name,
                        subtitle = def.conditionSummary,
                        feedback = feedback,
                        enabled = def.enabled,
                        showConflict = def.id in conflictDefIds,
                        showDelete = true,
                        onToggle = { onToggleDef(def.copy(enabled = it)) },
                        onEdit = { onEditDef(def) },
                        onDelete = { onDeleteDef(def) }
                    )
                }
            }
        }
    }
}

private fun triggerSummary(config: EventConfig): String = when (config.eventType) {
    EventType.SCREEN_LONG_USAGE -> "连续使用 ${config.threshold}分钟"
    EventType.LOW_BATTERY -> "电量低于 ${config.threshold}%"
    else -> "触发即响应"
}
