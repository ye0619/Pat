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

                    UnifiedEventCard(
                        name = EventConfig.displayName(config.eventType),
                        subtitle = triggerSummary(config),
                        feedback = feedback,
                        enabled = config.enabled,
                        showConflict = false,
                        onToggle = { onToggleConfig(config.copy(enabled = it)) },
                        onEdit = { onEditConfig(config) }
                    )
                }
                // 自定义事件
                items(customDefs.size) { idx ->
                    val def = customDefs[idx]
                    val feedback = def.reactions.joinToString(" | ") { it.text }
                    UnifiedEventCard(
                        name = def.name,
                        subtitle = def.conditionSummary,
                        feedback = feedback,
                        enabled = def.enabled,
                        showConflict = def.id in conflictDefIds,
                        onToggle = { onToggleDef(def.copy(enabled = it)) },
                        onEdit = { onEditDef(def) },
                        onDelete = { onDeleteDef(def) }
                    )
                }
            }
        }
    }
}

@Composable
private fun UnifiedEventCard(
    name: String, subtitle: String, feedback: String,
    enabled: Boolean, showConflict: Boolean,
    onToggle: (Boolean) -> Unit, onEdit: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
        containerColor = if (enabled) MaterialTheme.colorScheme.surface
        else MaterialTheme.colorScheme.surfaceVariant
    )) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        if (showConflict) {
                            Spacer(Modifier.width(4.dp))
                            Surface(shape = MaterialTheme.shapes.extraSmall,
                                color = MaterialTheme.colorScheme.errorContainer) {
                                Text("冲突", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                            }
                        }
                    }
                    Text(subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
            if (feedback.isNotBlank())
                Text(feedback, style = MaterialTheme.typography.bodyMedium, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (onDelete != null)
                    TextButton(onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("删除") }
                TextButton(onClick = onEdit) { Text("编辑") }
            }
        }
    }
}

private fun triggerSummary(config: EventConfig): String = when (config.eventType) {
    EventType.SCREEN_LONG_USAGE -> "连续使用 ${config.threshold}分钟"
    EventType.LOW_BATTERY -> "电量低于 ${config.threshold}%"
    else -> "触发即响应"
}
