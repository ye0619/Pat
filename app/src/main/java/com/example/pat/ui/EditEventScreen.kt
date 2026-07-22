package com.example.pat.ui

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

/**
 * 预设事件编辑页面。固定顶栏：返回 | 事件名称 | 保存 + 恢复默认
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditEventScreen(
    config: EventConfig,
    presetRepository: PresetRepository,
    onCreateCustomPreset: (EventType) -> Unit,
    onSave: (EventConfig) -> Unit,
    onPreviewAsset: (String) -> Unit,
    onRestoreDefaults: (EventType) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var enabled by remember { mutableStateOf(config.enabled) }
    var threshold by remember { mutableFloatStateOf(config.threshold.toFloat()) }
    var selectedPresetId by remember { mutableStateOf(config.presetId) }
    var reactions by remember {
        mutableStateOf(config.reactions.toMutableList().ifEmpty {
            mutableListOf(ReactionItem(text = config.customText, audioPath = config.customAudioPath))
        })
    }
    var notifEnabled by remember { mutableStateOf(config.notificationEnabled) }
    var headsUp by remember { mutableStateOf(config.showHeadsUp) }
    var playAudio by remember { mutableStateOf(config.soundEnabled) }
    var vibrate by remember { mutableStateOf(config.vibrationEnabled) }
    var lockScreen by remember { mutableStateOf(config.lockScreenPublic) }
    var minInterval by remember { mutableFloatStateOf(config.minIntervalMinutes.toFloat()) }

    val availablePresets = remember { presetRepository.getByEventType(config.eventType) }
    val selectedPreset = remember(selectedPresetId) { availablePresets.find { it.id == selectedPresetId } }

    val showSlider = config.eventType == EventType.SCREEN_LONG_USAGE
            || config.eventType == EventType.LOW_BATTERY
    val sliderLabel = when (config.eventType) {
        EventType.SCREEN_LONG_USAGE -> "分钟"; EventType.LOW_BATTERY -> "%"; else -> ""
    }
    val sliderRange = when (config.eventType) {
        EventType.SCREEN_LONG_USAGE -> 1f..1439f; EventType.LOW_BATTERY -> 1f..99f; else -> 0f..100f
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(EventConfig.displayName(config.eventType), fontWeight = FontWeight.Bold) },
            navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
            actions = {
                var menuExpanded by remember { mutableStateOf(false) }
                TextButton(onClick = {
                    onSave(buildConfig(config, enabled, threshold.toInt(), selectedPresetId, reactions,
                        notifEnabled, headsUp, playAudio, vibrate, lockScreen, minInterval.toInt()))
                }) { Text("保存", color = MaterialTheme.colorScheme.primary) }
                IconButton(onClick = { menuExpanded = true }) {
                    Text("⋮", style = MaterialTheme.typography.titleMedium)
                }
                DropdownMenu(menuExpanded, { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("恢复默认") }, onClick = {
                        menuExpanded = false; onRestoreDefaults(config.eventType)
                    })
                }
            })
    }, modifier = modifier.fillMaxSize()) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(8.dp))

            // 1. 启用事件
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("启用事件", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
            Spacer(Modifier.height(16.dp)); HorizontalDivider(); Spacer(Modifier.height(12.dp))

            // 2. 触发条件
            Text("触发条件", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            if (showSlider) {
                val valueText = "${threshold.toInt()}$sliderLabel"
                Text(when (config.eventType) {
                    EventType.SCREEN_LONG_USAGE -> "长时间使用：$valueText"
                    EventType.LOW_BATTERY -> "低电量：$valueText"
                    else -> valueText
                }, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                SliderWithInput(threshold, { threshold = it }, sliderRange, sliderLabel,
                    steps = sliderRange.endInclusive.toInt() - sliderRange.start.toInt() - 1)
            } else when (config.eventType) {
                EventType.CHARGE_START -> Text("触发条件：手机开始充电，无法更改",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                EventType.SHAKE -> ShakeConditionPanel()
                EventType.DROP -> {
                    Text("坠落：检测到短暂失重，随后发生强烈冲击",
                        style = MaterialTheme.typography.bodyMedium)
                    Text("使用XYZ合加速度和时间状态机判断，参数无法修改",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {}
            }

            Spacer(Modifier.height(16.dp)); HorizontalDivider(); Spacer(Modifier.height(12.dp))

            // 3. 选择反馈
            Text("选择反馈", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            if (availablePresets.isEmpty()) {
                Text("暂无可用预设", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.selectableGroup()) {
                        availablePresets.forEach { preset ->
                            FeedbackRadioRow(preset, preset.id == selectedPresetId, {
                                selectedPresetId = preset.id
                                reactions = mutableListOf(ReactionItem(text = preset.text, audioPath = preset.audioAssetPath))
                            }, { if (preset.audioAssetPath.isNotBlank()) onPreviewAsset(preset.audioAssetPath) })
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { onCreateCustomPreset(config.eventType) }) { Text("+ 新建反馈") }

            Spacer(Modifier.height(16.dp)); HorizontalDivider(); Spacer(Modifier.height(12.dp))

            // 4. 通知（全部默认关闭）
            Text("通知", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    NotifRow("横幅通知", "需在手机设置中打开横幅通知", headsUp) { headsUp = it }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    NotifRow("播放声音", "播放反馈中的音频文件", playAudio) { playAudio = it }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    NotifRow("振动", "", vibrate) { vibrate = it }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    NotifRow("锁屏显示内容", "", lockScreen) { lockScreen = it }
                }
            }

            Spacer(Modifier.height(16.dp)); HorizontalDivider(); Spacer(Modifier.height(12.dp))

            // 5. 最小触发时间间隔
            Text("最小触发时间间隔", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("${minInterval.toInt()}分钟", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            SliderWithInput(minInterval, { minInterval = it }, 0f..360f, "分钟", steps = 71)
            Text("同一事件在此时间内不会重复触发。0=无限制",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(32.dp))
        }
    }
}

private fun buildConfig(
    config: EventConfig, enabled: Boolean, threshold: Int, presetId: String,
    reactions: List<ReactionItem>, notif: Boolean, headsUp: Boolean,
    playAudio: Boolean, vibrate: Boolean, lockScreen: Boolean, minInterval: Int
) = config.copy(
    enabled = enabled, threshold = threshold, presetId = presetId,
    notificationEnabled = notif, showHeadsUp = headsUp,
    soundEnabled = playAudio, vibrationEnabled = vibrate,
    lockScreenPublic = lockScreen, minIntervalMinutes = minInterval,
    customText = reactions.firstOrNull()?.text ?: "",
    customAudioPath = reactions.firstOrNull()?.audioPath ?: "",
    reactions = reactions.filter { it.text.isNotBlank() || it.audioPath.isNotBlank() }
)

@Composable
private fun SliderWithInput(
    value: Float, onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>, label: String, steps: Int
) {
    var text by remember(value) { mutableStateOf(value.toInt().toString()) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Slider(value, { v -> onValueChange(v); text = v.toInt().toString() },
            valueRange = range, steps = steps, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(text, { s ->
            val f = s.filter { it.isDigit() }; text = f
            f.toFloatOrNull()?.let { onValueChange(it.coerceIn(range.start, range.endInclusive)) }
        }, Modifier.width(68.dp), singleLine = true, textStyle = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.width(2.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ShakeConditionPanel() {
    var amin by remember { mutableFloatStateOf(13f) }
    var amax by remember { mutableFloatStateOf(30f) }
    var n by remember { mutableIntStateOf(7) }
    var t by remember { mutableFloatStateOf(700f) }
    Text("摇晃手机（TYPE_LINEAR_ACCELERATION）", style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium)
    Text("加速度a在窗口t内，有n次位于(amin, amax)则触发",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    Text("amin: ${amin.toInt()} m/s²", style = MaterialTheme.typography.bodySmall)
    Slider(amin, { v -> amin = v }, valueRange = 5f..30f, modifier = Modifier.fillMaxWidth())
    Text("amax: ${amax.toInt()} m/s²", style = MaterialTheme.typography.bodySmall)
    Slider(amax, { v -> amax = v }, valueRange = 15f..50f, modifier = Modifier.fillMaxWidth())
    var nText by remember(n) { mutableStateOf(n.toString()) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("次数 n:", style = MaterialTheme.typography.bodySmall)
        Slider(n.toFloat(), { v -> n = v.toInt(); nText = v.toInt().toString() },
            valueRange = 3f..20f, steps = 16, modifier = Modifier.weight(1f))
        OutlinedTextField(nText, { s ->
            val f = s.filter { it.isDigit() }; nText = f
            f.toIntOrNull()?.coerceIn(3, 20)?.let { n = it }
        }, Modifier.width(52.dp), singleLine = true, textStyle = MaterialTheme.typography.bodySmall)
    }
    Text("窗口 t: ${t.toInt()}ms", style = MaterialTheme.typography.bodySmall)
    Slider(t, { v -> t = v }, valueRange = 2000f..10000f, steps = 15, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    Button(onClick = {}, Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
        Text("摇晃测试 (${t.toInt()}ms)")
    }
}

@Composable
private fun FeedbackRadioRow(
    preset: ReactionPreset, isSelected: Boolean,
    onSelect: () -> Unit, onPreview: () -> Unit
) {
    Row(Modifier.fillMaxWidth()
        .selectable(selected = isSelected, onClick = onSelect, role = Role.RadioButton)
        .padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = isSelected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(preset.text, style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
            Text(preset.name, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (preset.audioAssetPath.isNotBlank()) TextButton(onClick = onPreview) { Text("试听") }
    }
}

@Composable
private fun NotifRow(label: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (desc.isNotBlank()) Text(desc, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
    }
}
