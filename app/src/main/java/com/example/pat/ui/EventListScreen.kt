package com.example.pat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.pat.config.EventConfig
import com.example.pat.event.EventType
import com.example.pat.ui.theme.PatTheme

/**
 * 事件管理列表页。
 *
 * 显示所有可配置事件类型，每个条目包含：
 * - 事件名称
 * - 启用/禁用开关
 * - 阈值（如有）
 * - 反馈文本预览
 * - 编辑按钮
 */
@Composable
fun EventListScreen(
    configs: List<EventConfig>,
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
            TextButton(onClick = onBack) {
                Text("< 返回")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "事件管理",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── 事件列表 ──
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(configs) { config ->
                EventConfigCard(
                    config = config,
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 标题行：名称 + 开关
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
                    // 显示阈值（如有）
                    if (config.threshold > 0 && config.eventType == EventType.SCREEN_LONG_USAGE) {
                        Text(
                            text = "阈值: ${config.threshold}分钟",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (config.threshold > 0 && config.eventType == EventType.LOW_BATTERY) {
                        Text(
                            text = "阈值: ${config.threshold}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = config.enabled,
                    onCheckedChange = onToggleEnabled
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 反馈文本预览
            Text(
                text = "反馈: ${config.text}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // 语音状态
            if (config.voicePath.isNotBlank()) {
                Text(
                    text = "语音: 已上传",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 编辑按钮
            TextButton(
                onClick = onEditClick,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("编辑")
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
                    id = "1",
                    eventType = EventType.SCREEN_LONG_USAGE,
                    enabled = true,
                    threshold = 120,
                    text = "别看了，我想睡觉了"
                ),
                EventConfig(
                    id = "2",
                    eventType = EventType.CHARGE_START,
                    enabled = true,
                    text = "谢谢给我补充能量"
                ),
                EventConfig(
                    id = "3",
                    eventType = EventType.LOW_BATTERY,
                    enabled = true,
                    threshold = 20,
                    text = "我要没电啦"
                ),
                EventConfig(
                    id = "4",
                    eventType = EventType.SHAKE,
                    enabled = true,
                    text = "别摇我"
                )
            ),
            onToggleEnabled = {},
            onEditClick = {},
            onBack = {}
        )
    }
}
