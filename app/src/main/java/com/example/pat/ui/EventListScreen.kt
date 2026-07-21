package com.example.pat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.pat.data.PresetRepository
import com.example.pat.event.EventType
import com.example.pat.model.EventConfig
import com.example.pat.model.ReactionPreset
import com.example.pat.ui.theme.PatTheme

/**
 * 事件管理列表页 —— 展示所有事件规则及其关联的反馈预设。
 *
 * 每个条目显示：
 * - 事件名称 + 启用状态
 * - 触发条件（阈值）
 * - 关联的反馈预设（文本 + 语音）
 * - 通知开关
 * - 编辑按钮
 */
@Composable
fun EventListScreen(
    configs: List<EventConfig>,
    presetRepository: PresetRepository?,
    onToggleEnabled: (EventConfig) -> Unit,
    onEditClick: (EventType) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        // ── 标题栏 ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("< 返回") }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "事件管理",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            // ── 标题行：名称 + 开关 ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = EventConfig.displayName(config.eventType),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    // 状态
                    Text(
                        text = "状态: ${if (config.enabled) "开启" else "关闭"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = config.enabled, onCheckedChange = onToggleEnabled)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── 触发条件 ──
            val conditionText = when (config.eventType) {
                EventType.SCREEN_LONG_USAGE -> "连续使用 ${config.threshold}分钟"
                EventType.LOW_BATTERY -> "电量低于 ${config.threshold}%"
                else -> "触发即响应"
            }
            Text(
                text = "触发条件: $conditionText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            // ── 反馈预设信息 ──
            if (preset != null) {
                Text(
                    text = "反馈: ${preset.text}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val audioLabel = when {
                    preset.audioAssetPath.isNotBlank() -> "语音: ${preset.audioAssetPath}"
                    else -> "语音: 无"
                }
                Text(
                    text = audioLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = "反馈: ${EventConfig.defaultText(config.eventType)} (默认)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "语音: 无",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── 最小触发间隔 ──
            Text(
                text = if (config.minIntervalMinutes > 0) "最小间隔: ${config.minIntervalMinutes}分钟"
                       else "最小间隔: 无限制",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ── 通知状态 ──
            Text(
                text = "通知: ${if (config.notificationEnabled) "开启" else "关闭"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── 编辑按钮 ──
            TextButton(
                onClick = onEditClick,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("编辑事件")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EventListScreenPreview() {
    PatTheme {
        EventListScreen(
            configs = listOf(
                EventConfig(
                    id = "1", eventType = EventType.SCREEN_LONG_USAGE,
                    enabled = true, threshold = 120, presetId = "p1", notificationEnabled = true
                ),
                EventConfig(
                    id = "2", eventType = EventType.CHARGE_START,
                    enabled = true, threshold = 0, presetId = "", notificationEnabled = true
                )
            ),
            presetRepository = null,
            onToggleEnabled = {}, onEditClick = {}, onBack = {}
        )
    }
}
