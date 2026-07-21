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
 * 统一事件管理列表 — v2：基础事件 + 自定义规则分区显示。
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
            // 新建自定义规则按钮
            IconButton(onClick = onCreateClick) {
                Text("+", style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // ═══════════════════════════════════════════
            // Section 1: 基础事件
            // ═══════════════════════════════════════════
            item {
                SectionHeader(title = "基础事件", subtitle = "系统内置，轻点编辑反馈内容")
            }

            items(configs.size) { index ->
                val config = configs[index]
                val preset = config.presetId.let { id ->
                    if (id.isNotBlank()) presetRepository?.getById(id) else null
                }
                // 显示反馈池中的文本摘要
                val feedbackPreview = if (config.reactions.isNotEmpty()) {
                    config.reactions.joinToString(" | ") { it.text }.ifBlank { EventConfig.defaultText(config.eventType) }
                } else {
                    preset?.text ?: config.customText.ifBlank { EventConfig.defaultText(config.eventType) }
                }

                BaseEventCard(
                    name = EventConfig.displayName(config.eventType),
                    subtitle = when (config.eventType) {
                        EventType.SCREEN_LONG_USAGE -> "连续使用 ${config.threshold}分钟"
                        EventType.LOW_BATTERY -> "电量低于 ${config.threshold}%"
                        else -> "触发即响应"
                    },
                    feedback = feedbackPreview,
                    enabled = config.enabled,
                    onToggleEnabled = { onToggleEnabled(config.copy(enabled = it)) },
                    onEdit = { onEditClick(config.eventType) }
                )
            }

            // ═══════════════════════════════════════════
            // Section 2: 自定义规则
            // ═══════════════════════════════════════════
            item {
                Spacer(Modifier.height(8.dp))
                SectionHeader(
                    title = "自定义规则",
                    subtitle = if (userRules.isEmpty()) "暂无规则，点击 + 创建" else "用户创建的组合条件规则"
                )
            }

            if (userRules.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = "点击右上角 + 创建第一条自定义规则",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(userRules.size) { index ->
                    val rule = userRules[index]
                    val feedbackPreview = if (rule.reactions.isNotEmpty()) {
                        rule.reactions.joinToString(" | ") { it.text }
                    } else {
                        rule.reactionText.ifBlank { "（无自定义文本）" }
                    }

                    CustomRuleCard(
                        name = rule.name,
                        subtitle = rule.conditionSummary,
                        feedback = feedbackPreview,
                        priority = rule.priority,
                        enabled = rule.enabled,
                        onToggleEnabled = { onToggleRuleEnabled(rule.copy(enabled = it)) },
                        onEdit = { onEditRuleClick(rule) },
                        onDelete = { onDeleteRuleClick(rule) }
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 基础事件卡片 */
@Composable
private fun BaseEventCard(
    name: String,
    subtitle: String,
    feedback: String,
    enabled: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onEdit: () -> Unit
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
                    Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = enabled, onCheckedChange = onToggleEnabled)
            }
            if (feedback.isNotBlank()) {
                Text(feedback, style = MaterialTheme.typography.bodyMedium, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onEdit) { Text("编辑") }
            }
        }
    }
}

/** 自定义规则卡片 */
@Composable
private fun CustomRuleCard(
    name: String,
    subtitle: String,
    feedback: String,
    priority: Int,
    enabled: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
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
                        Spacer(Modifier.width(4.dp))
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text("自定义", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                        }
                    }
                    Text(subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = enabled, onCheckedChange = onToggleEnabled)
            }
            if (feedback.isNotBlank()) {
                Text(feedback, style = MaterialTheme.typography.bodyMedium, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("优先级: $priority", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row {
                    TextButton(onClick = onEdit) { Text("编辑") }
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
