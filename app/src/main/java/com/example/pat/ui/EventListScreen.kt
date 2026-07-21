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
import com.example.pat.model.ReactionPreset
import com.example.pat.model.UserRule

/**
 * 统一事件管理列表 — 基础事件 + 自定义规则混合显示。
 */
@Composable
fun EventListScreen(
    configs: List<EventConfig>,
    userRules: List<UserRule>,
    presetRepository: PresetRepository?,
    onToggleEnabled: (EventConfig) -> Unit,
    onToggleRuleEnabled: (UserRule) -> Unit,
    onEditClick: (EventType) -> Unit,
    onEditRuleClick: (UserRule) -> Unit,
    onDeleteRuleClick: (UserRule) -> Unit,
    onCreateClick: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var sortByPriority by remember { mutableStateOf(false) }

    // 统一列表项
    val items: List<Any> = remember(configs, userRules, sortByPriority) {
        val all = mutableListOf<Any>()
        all.addAll(configs)
        all.addAll(userRules)
        if (sortByPriority) {
            all.sortByDescending {
                when (it) {
                    is EventConfig -> it.priority
                    is UserRule -> it.priority
                    else -> 0
                }
            }
        } else {
            // 默认排序：基础事件按类型顺序，自定义规则按名称
            all.sortWith(compareBy<Any> {
                when (it) {
                    is EventConfig -> 0  // basic first
                    is UserRule -> 1     // custom after
                    else -> 2
                }
            }.thenBy {
                when (it) {
                    is EventConfig -> it.eventType.ordinal
                    is UserRule -> it.name
                    else -> ""
                }
            })
        }
        all
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // ── 标题栏 ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("< 返回") }
            Spacer(Modifier.width(4.dp))
            Text("事件管理", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            // 排序切换
            IconButton(onClick = { sortByPriority = !sortByPriority }) {
                Text(if (sortByPriority) "🔢" else "📋", style = MaterialTheme.typography.titleSmall)
            }
            // 新建按钮
            IconButton(onClick = onCreateClick) {
                Text("+", style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(Modifier.height(4.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(items.size) { index ->
                val item = items[index]
                when (item) {
                    is EventConfig -> {
                        val preset = item.presetId.let { id ->
                            if (id.isNotBlank()) presetRepository?.getById(id) else null
                        }
                        EventCard(
                            name = EventConfig.displayName(item.eventType),
                            subtitle = when (item.eventType) {
                                EventType.SCREEN_LONG_USAGE -> "连续使用 ${item.threshold}分钟"
                                EventType.LOW_BATTERY -> "电量低于 ${item.threshold}%"
                                else -> "触发即响应"
                            },
                            feedback = preset?.text ?: EventConfig.defaultText(item.eventType),
                            priority = item.priority,
                            enabled = item.enabled,
                            isBasic = true,
                            onToggleEnabled = { onToggleEnabled(item.copy(enabled = it)) },
                            onEdit = { onEditClick(item.eventType) },
                            onDelete = null
                        )
                    }
                    is UserRule -> {
                        EventCard(
                            name = item.name,
                            subtitle = item.conditionSummary,
                            feedback = item.reactionText.ifBlank { "（无自定义文本）" },
                            priority = item.priority,
                            enabled = item.enabled,
                            isBasic = false,
                            onToggleEnabled = { onToggleRuleEnabled(item.copy(enabled = it)) },
                            onEdit = { onEditRuleClick(item) },
                            onDelete = { onDeleteRuleClick(item) }
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun EventCard(
    name: String,
    subtitle: String,
    feedback: String,
    priority: Int,
    enabled: Boolean,
    isBasic: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        if (!isBasic) {
                            Spacer(Modifier.width(4.dp))
                            Text("自定义", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Text(subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = enabled, onCheckedChange = onToggleEnabled)
            }
            if (feedback.isNotBlank()) {
                Text(feedback, style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("优先级: $priority", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row {
                    TextButton(onClick = onEdit) { Text("编辑") }
                    if (onDelete != null) {
                        TextButton(onClick = onDelete,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) { Text("删除") }
                    }
                }
            }
        }
    }
}
