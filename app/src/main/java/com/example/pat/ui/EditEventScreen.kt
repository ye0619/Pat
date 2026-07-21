package com.example.pat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.pat.data.PresetRepository
import com.example.pat.event.EventType
import com.example.pat.model.EventConfig
import com.example.pat.model.ReactionPreset
import com.example.pat.ui.theme.PatTheme

/**
 * 事件编辑页面 —— 配置事件规则并选择反馈预设。
 *
 * 功能：
 * - 启用/禁用开关
 * - 阈值滑块（适用的事件类型）
 * - 预设选择（RadioButton 列表 + 自定义选项）
 * - 通知开关
 * - 试听当前选中预设
 * - 保存
 */
@Composable
fun EditEventScreen(
    config: EventConfig,
    presetRepository: PresetRepository,
    onCreateCustomPreset: (EventType) -> Unit,
    onSave: (EventConfig) -> Unit,
    onPreviewAsset: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // ── 编辑状态 ──
    var enabled by remember { mutableStateOf(config.enabled) }
    var threshold by remember { mutableFloatStateOf(config.threshold.toFloat()) }
    var selectedPresetId by remember { mutableStateOf(config.presetId) }
    var notificationEnabled by remember { mutableStateOf(config.notificationEnabled) }

    // ── 可用预设列表 ──
    val availablePresets = remember {
        presetRepository.getByEventType(config.eventType)
    }
    val selectedPreset = remember(selectedPresetId) {
        availablePresets.find { it.id == selectedPresetId }
    }

    val showThreshold = config.eventType == EventType.SCREEN_LONG_USAGE
            || config.eventType == EventType.LOW_BATTERY

    val thresholdLabel = when (config.eventType) {
        EventType.SCREEN_LONG_USAGE -> "分钟"
        EventType.LOW_BATTERY -> "%"
        else -> ""
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // ── 标题栏 ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("< 返回") }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "编辑 - ${EventConfig.displayName(config.eventType)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── 启用开关 ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("启用事件", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Switch(checked = enabled, onCheckedChange = { enabled = it })
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── 阈值滑块 ──
        if (showThreshold) {
            Text(
                text = "触发条件: ${threshold.toInt()}$thresholdLabel",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))

            val sliderRange = when (config.eventType) {
                EventType.SCREEN_LONG_USAGE -> 30f..300f
                EventType.LOW_BATTERY -> 5f..50f
                else -> 0f..100f
            }

            Slider(
                value = threshold,
                onValueChange = { threshold = it },
                valueRange = sliderRange,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(20.dp))
        }

        // ── 反馈预设选择 ──
        Text(
            text = "选择反馈预设",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (availablePresets.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    text = "暂无可用预设，请创建自定义预设",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.selectableGroup()) {
                    availablePresets.forEach { preset ->
                        val isSelected = preset.id == selectedPresetId
                        PresetRadioRow(
                            preset = preset,
                            isSelected = isSelected,
                            onSelect = { selectedPresetId = preset.id },
                            onPreview = {
                                if (preset.audioAssetPath.isNotBlank()) {
                                    onPreviewAsset(preset.audioAssetPath)
                                }
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── 自定义预设入口 ──
        TextButton(
            onClick = { onCreateCustomPreset(config.eventType) }
        ) {
            Text("+ 创建自定义预设")
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── 当前选中预设详情 ──
        if (selectedPreset != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "已选: ${selectedPreset.name}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "文本: ${selectedPreset.text}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (selectedPreset.audioAssetPath.isNotBlank()) {
                        Text(
                            text = "音频: ${selectedPreset.audioAssetPath}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { onPreviewAsset(selectedPreset.audioAssetPath) },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Text("试听")
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // ── 通知开关 ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("通知", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text("事件触发时发送通知", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = notificationEnabled, onCheckedChange = { notificationEnabled = it })
        }

        Spacer(modifier = Modifier.height(32.dp))

        // ── 保存按钮 ──
        Button(
            onClick = {
                onSave(
                    config.copy(
                        enabled = enabled,
                        threshold = threshold.toInt(),
                        presetId = selectedPresetId,
                        notificationEnabled = notificationEnabled
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("保存", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 4.dp))
        }
    }
}

/**
 * 预设单选行 —— RadioButton + 文本 + 试听按钮。
 */
@Composable
private fun PresetRadioRow(
    preset: ReactionPreset,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onPreview: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = isSelected,
                onClick = onSelect,
                role = Role.RadioButton
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = null  // handled by selectable modifier
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = preset.text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                text = preset.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (preset.audioAssetPath.isNotBlank()) {
            TextButton(onClick = onPreview) { Text("试听") }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EditEventScreenPreview() {
    PatTheme {
        // Preview only — cannot instantiate real repository here
    }
}
