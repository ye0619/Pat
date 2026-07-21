package com.example.pat.ui

import androidx.compose.animation.animateContentSize
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
import androidx.compose.ui.unit.dp
import com.example.pat.data.PresetRepository
import com.example.pat.event.EventType
import com.example.pat.model.EventConfig
import com.example.pat.model.ReactionItem
import com.example.pat.model.ReactionPreset
import com.example.pat.ui.theme.PatTheme

/**
 * 基础事件编辑页面 —— 配置反馈内容（文本+音频池）和通知方式。
 *
 * v2 改进：
 * - 多 ReactionItem 编辑（+ 添加按钮，列表展示，每项包含 text + audio）
 * - 预设选择：RadioButton 列表 + 选中后自动填充反应池
 * - 高级设置折叠（priority, cooldown）
 * - 阈值仅对 LONG_USAGE / LOW_BATTERY 显示
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    var priority by remember { mutableFloatStateOf(config.priority.toFloat()) }

    // v2: 反应池编辑 — 从 config.reactions 或预设/customText+customAudioPath 初始化
    var reactions by remember {
        mutableStateOf(
            if (config.reactions.isNotEmpty()) config.reactions.toMutableList()
            else mutableListOf(ReactionItem(text = config.customText, audioPath = config.customAudioPath))
        )
    }

    // 高级设置折叠
    var showAdvanced by remember { mutableStateOf(false) }

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
            Spacer(Modifier.weight(1f))
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = "基础事件",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── 启用开关 ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("启用事件", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Switch(checked = enabled, onCheckedChange = { enabled = it })
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── 阈值滑块（仅适用类型） ──
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
            Spacer(modifier = Modifier.height(16.dp))
        }

        // ═══════════════════════════════════════════
        // 预设选择（保持原有 RadioButton 列表）
        // ═══════════════════════════════════════════
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
                            onSelect = {
                                selectedPresetId = preset.id
                                // 选中预设时自动填充反应池
                                reactions = mutableListOf(
                                    ReactionItem(text = preset.text, audioPath = preset.audioAssetPath)
                                )
                            },
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

        TextButton(onClick = { onCreateCustomPreset(config.eventType) }) {
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
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
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
                        }
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

        // ═══════════════════════════════════════════
        // 反馈池编辑（v2：多 ReactionItem）
        // ═══════════════════════════════════════════
        Text(
            text = "反馈内容池（高级）",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "触发时随机选择一条。预设选择会自动填充第一项。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        reactions.forEachIndexed { index, item ->
            ReactionItemCard(
                index = index,
                item = item,
                onUpdate = { updated ->
                    reactions = reactions.toMutableList().also { it[index] = updated }
                },
                onRemove = {
                    if (reactions.size > 1) {
                        reactions = reactions.toMutableList().also { it.removeAt(index) }
                    }
                },
                onPreview = { path ->
                    if (path.isNotBlank()) onPreviewAsset(path)
                },
                canRemove = reactions.size > 1
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        TextButton(onClick = {
            reactions = reactions.toMutableList().also { it.add(ReactionItem()) }
        }) {
            Text("+ 添加反馈项")
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
                NotificationSwitchRow(
                    label = "通知总开关",
                    description = "事件触发时发送通知",
                    checked = notificationEnabled,
                    onCheckedChange = { notificationEnabled = it }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                NotificationSwitchRow(
                    label = "弹窗横幅",
                    description = "屏幕顶部弹出横幅提醒",
                    checked = showHeadsUp,
                    onCheckedChange = { showHeadsUp = it },
                    enabled = notificationEnabled
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                NotificationSwitchRow(
                    label = "播放声音",
                    description = "系统通知提示音",
                    checked = soundEnabled,
                    onCheckedChange = { soundEnabled = it },
                    enabled = notificationEnabled
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                NotificationSwitchRow(
                    label = "开启震动",
                    description = "通知时震动（默认关闭）",
                    checked = vibrationEnabled,
                    onCheckedChange = { vibrationEnabled = it },
                    enabled = notificationEnabled
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                NotificationSwitchRow(
                    label = "锁屏显示内容",
                    description = "锁屏时显示通知详情",
                    checked = lockScreenPublic,
                    onCheckedChange = { lockScreenPublic = it },
                    enabled = notificationEnabled
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── 高级设置（折叠） ──
        Card(
            modifier = Modifier.fillMaxWidth().animateContentSize(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showAdvanced = !showAdvanced },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("高级设置", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text(if (showAdvanced) "▲" else "▼")
                }

                if (showAdvanced) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("优先级: ${priority.toInt()}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "多个事件同时触发时，高优先级事件优先执行",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(value = priority, onValueChange = { priority = it },
                        valueRange = 1f..10f, steps = 8, modifier = Modifier.fillMaxWidth())

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "最小触发间隔: ${minIntervalMinutes.toInt()}分钟",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "同一事件在此时间内不会重复触发。0 = 无限制。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = minIntervalMinutes,
                        onValueChange = { minIntervalMinutes = it },
                        valueRange = 0f..60f,
                        steps = 11,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

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
                        minIntervalMinutes = minIntervalMinutes.toInt(),
                        priority = priority.toInt(),
                        customText = reactions.firstOrNull()?.text ?: "",
                        customAudioPath = reactions.firstOrNull()?.audioPath ?: "",
                        reactions = reactions.filter { it.text.isNotBlank() || it.audioPath.isNotBlank() }
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = reactions.any { it.text.isNotBlank() },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("保存", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 4.dp))
        }

        Spacer(modifier = Modifier.height(32.dp))
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
 * 单个 ReactionItem 编辑卡片。
 */
@Composable
private fun ReactionItemCard(
    index: Int,
    item: ReactionItem,
    onUpdate: (ReactionItem) -> Unit,
    onRemove: () -> Unit,
    onPreview: (String) -> Unit,
    canRemove: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("反馈 #${index + 1}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f))
                if (item.audioPath.isNotBlank()) {
                    TextButton(onClick = { onPreview(item.audioPath) }) { Text("试听", style = MaterialTheme.typography.labelSmall) }
                }
                if (canRemove) {
                    TextButton(onClick = onRemove) {
                        Text("删除", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = item.text,
                onValueChange = { onUpdate(item.copy(text = it)) },
                label = { Text("文本") },
                placeholder = { Text("触发时显示的文字") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = item.audioPath,
                onValueChange = { onUpdate(item.copy(audioPath = it)) },
                label = { Text("音频路径（可选）") },
                placeholder = { Text("assets/xxx.wav 或 /data/.../xxx.audio") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * 通知设置开关行。
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
