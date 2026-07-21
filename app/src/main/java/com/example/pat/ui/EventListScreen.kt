package com.example.pat.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
 * 事件管理列表页 —— 统一展示基础事件规则 + 用户自定义规则。
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
    onCreateRuleClick: () -> Unit,
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
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "事件管理",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // ── 基础事件 ──
            item {
                Text(
                    text = "基础事件",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            items(configs) { config ->
                val preset = config.presetId.let { id ->
                    if (id.isNotBlank()) presetRepository?.getById(id) else null
                }
                EventConfigCard(
                    config = config,
                    preset = preset,
                    onToggleEnabled = { onToggleEnabled(config.copy(enabled = it)) },
                    onEditClick = { onEditClick(config.eventType) }
                )
            }

            // ── 自定义规则 ──
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "自定义规则",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onCreateRuleClick) {
                        Text("+ 新建")
                    }
                }
            }

            if (userRules.isEmpty()) {
                item {
                    Text(
                        text = "暂无自定义规则，点击「+ 新建」创建",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            items(userRules) { rule ->
                UserRuleCard(
                    rule = rule,
                    onToggleEnabled = { onToggleRuleEnabled(rule.copy(enabled = it)) },
                    onEditClick = { onEditRuleClick(rule) },
                    onDeleteClick = { onDeleteRuleClick(rule) }
                )
            }

            // 底部留白
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun EventConfigCard(
    config: EventConfig,
    preset: ReactionPreset?,
    onToggleEnabled: (Boolean) -> Unit,
    onEditClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (config.enabled)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = EventConfig.displayName(config.eventType),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    val conditionText = when (config.eventType) {
                        EventType.SCREEN_LONG_USAGE -> "连续使用 ${config.threshold}分钟"
                        EventType.LOW_BATTERY -> "电量低于 ${config.threshold}%"
                        else -> "触发即响应"
                    }
                    Text(
                        text = conditionText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = config.enabled, onCheckedChange = onToggleEnabled)
            }

            // 反馈信息
            val text = preset?.text ?: EventConfig.defaultText(config.eventType)
            if (text.isNotBlank()) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onEditClick) { Text("编辑") }
            }
        }
    }
}

@Composable
private fun UserRuleCard(
    rule: UserRule,
    onToggleEnabled: (Boolean) -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (rule.enabled)
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rule.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = rule.conditionSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Switch(checked = rule.enabled, onCheckedChange = onToggleEnabled)
            }

            // 反馈
            if (rule.reactionText.isNotBlank()) {
                Text(
                    text = rule.reactionText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${rule.timeWindowMs / 1000}秒 · 优先级${rule.priority}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row {
                    TextButton(onClick = onEditClick) { Text("编辑") }
                    TextButton(
                        onClick = onDeleteClick,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text("删除") }
                }
            }
        }
    }
}
