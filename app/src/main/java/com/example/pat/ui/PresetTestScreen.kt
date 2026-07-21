package com.example.pat.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.pat.audio.AudioPlayer
import com.example.pat.config.EventConfig
import com.example.pat.event.EventType
import com.example.pat.preset.PresetReaction
import com.example.pat.ui.theme.PatTheme

/**
 * 预设测试页面 —— 查看和试听所有内置预设反馈。
 *
 * 功能：
 * - 按事件类型分组显示所有预设
 * - 每个预设显示文本内容
 * - "试听"按钮播放对应音频
 *
 * 用于开发和测试预设系统的完整性。
 *
 * 参考：Task 7 - 预设测试页面
 */
@Composable
fun PresetTestScreen(
    groupedPresets: Map<EventType, List<PresetReaction>>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val audioPlayer = remember { AudioPlayer(context) }

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
                text = "预设测试",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "共 ${groupedPresets.values.sumOf { it.size }} 个预设，覆盖 ${groupedPresets.size} 种事件类型",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (groupedPresets.isEmpty()) {
            // 空状态
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "未加载到任何预设",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "请检查 res/raw 目录中是否存在预设音频文件\n" +
                                "文件命名格式: 事件类型（文本内容）.wav",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                groupedPresets.entries.forEachIndexed { _, (eventType, presets) ->
                    item(key = "header_${eventType.name}") {
                        Text(
                            text = EventConfig.displayName(eventType),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    items(
                        items = presets,
                        key = { it.id }
                    ) { preset ->
                        PresetCard(
                            preset = preset,
                            onPreview = { audioPlayer.playAsset(preset.audioAssetPath) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 单个预设卡片 —— 显示文本 + 试听按钮。
 */
@Composable
private fun PresetCard(
    preset: PresetReaction,
    onPreview: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 文本内容
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = preset.displayText,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "路径: ${preset.audioAssetPath}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 试听按钮
            FilledTonalButton(
                onClick = onPreview
            ) {
                Text("试听")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PresetTestScreenPreview() {
    PatTheme {
        PresetTestScreen(
            groupedPresets = mapOf(
                EventType.SCREEN_LONG_USAGE to listOf(
                    PresetReaction(
                        id = "1",
                        eventType = EventType.SCREEN_LONG_USAGE,
                        displayText = "别看了，我想睡觉了",
                        audioAssetPath = "长时间使用（别看了，我想睡觉了）.wav"
                    ),
                    PresetReaction(
                        id = "2",
                        eventType = EventType.SCREEN_LONG_USAGE,
                        displayText = "一天天的，就知道玩手机",
                        audioAssetPath = "长时间使用（一天天的，就知道玩手机）.wav"
                    )
                ),
                EventType.SHAKE to listOf(
                    PresetReaction(
                        id = "3",
                        eventType = EventType.SHAKE,
                        displayText = "别晃了~晃得我头都晕了",
                        audioAssetPath = "摇晃手机（别晃了~晃得我头都晕了）.wav"
                    )
                )
            ),
            onBack = {}
        )
    }
}
