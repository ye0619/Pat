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
import com.example.pat.event.EventType
import com.example.pat.model.AudioType
import com.example.pat.model.EventConfig
import com.example.pat.model.ReactionPreset
import com.example.pat.ui.theme.PatTheme

/**
 * 预设测试页面 —— 查看和试听所有预设反馈。
 *
 * 按事件类型分组显示所有预设，每个预设可试听。
 */
@Composable
fun PresetTestScreen(
    groupedPresets: Map<EventType, List<ReactionPreset>>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val audioPlayer = remember { AudioPlayer(context) }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("< 返回") }
            Spacer(modifier = Modifier.width(8.dp))
            Text("预设测试", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "共 ${groupedPresets.values.sumOf { it.size }} 个预设，覆盖 ${groupedPresets.size} 种事件类型",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (groupedPresets.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("未加载到任何预设", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "请检查 assets/ 目录中是否存在预设音频文件",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                groupedPresets.entries.forEachIndexed { _, (eventType, presets) ->
                    item(key = "h_${eventType.name}") {
                        Text(
                            text = "${EventConfig.displayName(eventType)} (${presets.size}个)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    items(items = presets, key = { it.id }) { preset ->
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

@Composable
private fun PresetCard(preset: ReactionPreset, onPreview: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(preset.text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.width(6.dp))
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                if (preset.audioType == AudioType.PRESET) "内置" else "自定义",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.height(24.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text("${preset.name} · ${preset.audioAssetPath}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.width(12.dp))
            if (preset.audioAssetPath.isNotBlank()) {
                FilledTonalButton(onClick = onPreview) { Text("试听") }
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
                    ReactionPreset("1", "休息提醒", "别看了，我想睡觉了", "test.wav", AudioType.PRESET, EventType.SCREEN_LONG_USAGE),
                    ReactionPreset("2", "别玩了", "一天天的，就知道玩手机", "test2.wav", AudioType.PRESET, EventType.SCREEN_LONG_USAGE)
                )
            ),
            onBack = {}
        )
    }
}
