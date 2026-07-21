package com.example.pat.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
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
    var vibrationEnabled by remember { mutableStateOf(config.vibrationEnabled) }
    var soundEnabled by remember { mutableStateOf(config.soundEnabled) }
    var showHeadsUp by remember { mutableStateOf(config.showHeadsUp) }
    var lockScreenPublic by remember { mutableStateOf(config.lockScreenPublic) }
    var minIntervalMinutes by remember { mutableFloatStateOf(config.minIntervalMinutes.toFloat()) }
    var customText by remember { mutableStateOf(config.customText) }
    var customAudioPath by remember { mutableStateOf(config.customAudioPath) }

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

    val context = LocalContext.current
    val audioFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val fileName = "custom_audio_${System.currentTimeMillis()}.audio"
                val outputFile = java.io.File(context.filesDir, fileName)
                inputStream?.use { input -> outputFile.outputStream().use { output -> input.copyTo(output) } }
                customAudioPath = outputFile.absolutePath
            } catch (_: Exception) {}
        }
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

        // ── 自定义反馈文本/音频（覆盖预设） ──
        Text(
            text = "自定义反馈（可选，覆盖预设）",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = customText,
            onValueChange = { customText = it },
            label = { Text("反馈文本") },
            placeholder = { Text(EventConfig.defaultText(config.eventType)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { audioFilePicker.launch("audio/*") },
                modifier = Modifier.weight(1f)
            ) { Text("选择音频", style = MaterialTheme.typography.labelMedium) }
            if (customAudioPath.isNotBlank()) {
                TextButton(onClick = { customAudioPath = "" }) { Text("清除音频") }
            }
        }
        if (customAudioPath.isNotBlank()) {
            Text(
                text = "已选: ${customAudioPath.takeLast(35)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        // ── 通知设置 ──
        Text(
            text = "通知设置",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {

                // 通知总开关
                NotificationSwitchRow(
                    label = "通知总开关",
                    description = "事件触发时发送通知",
                    checked = notificationEnabled,
                    onCheckedChange = { notificationEnabled = it }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // 弹窗横幅
                NotificationSwitchRow(
                    label = "弹窗横幅",
                    description = "屏幕顶部弹出横幅提醒",
                    checked = showHeadsUp,
                    onCheckedChange = { showHeadsUp = it },
                    enabled = notificationEnabled
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // 播放声音
                NotificationSwitchRow(
                    label = "播放声音",
                    description = "系统通知提示音",
                    checked = soundEnabled,
                    onCheckedChange = { soundEnabled = it },
                    enabled = notificationEnabled
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // 震动
                NotificationSwitchRow(
                    label = "开启震动",
                    description = "通知时震动（默认关闭）",
                    checked = vibrationEnabled,
                    onCheckedChange = { vibrationEnabled = it },
                    enabled = notificationEnabled
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // 锁屏显示
                NotificationSwitchRow(
                    label = "锁屏显示内容",
                    description = "锁屏时显示通知详情",
                    checked = lockScreenPublic,
                    onCheckedChange = { lockScreenPublic = it },
                    enabled = notificationEnabled
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── 最小触发间隔 ──
        Text(
            text = "最小触发间隔: ${minIntervalMinutes.toInt()}分钟",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "同一事件在此时间内不会重复触发。设为 0 表示每次检测都触发。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Slider(
            value = minIntervalMinutes,
            onValueChange = { minIntervalMinutes = it },
            valueRange = 0f..60f,
            steps = 11,  // 0, 5, 10, 15, ..., 60
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(32.dp))

        // ── 保存按钮 ──
        Button(
            onClick = {
                onSave(
                    config.copy(
                        enabled = enabled,
                        threshold = threshold.toInt(),
                        presetId = selectedPresetId,
                        notificationEnabled = notificationEnabled,
                        vibrationEnabled = vibrationEnabled,
                        soundEnabled = soundEnabled,
                        showHeadsUp = showHeadsUp,
                        lockScreenPublic = lockScreenPublic,
                        customText = customText,
                        customAudioPath = customAudioPath,
                        minIntervalMinutes = minIntervalMinutes.toInt()
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

/**
 * 通知设置开关行 —— 标签 + 描述 + Switch。
 */
@Composable
private fun NotificationSwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (enabled)
                    MaterialTheme.colorScheme.onSurface
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked && enabled,
            onCheckedChange = { if (enabled) onCheckedChange(it) },
            enabled = enabled
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EditEventScreenPreview() {
    PatTheme {
        // Preview only — cannot instantiate real repository here
    }
}
