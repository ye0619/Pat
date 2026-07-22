package com.example.pat.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pat.event.AtomicEventType
import com.example.pat.model.*

/**
 * 新建/编辑事件界面。固定顶栏：返回 | 新建事件 | 保存
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewEventScreen(
    existing: EventDefinition? = null,
    onSave: (EventDefinition) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isEditing = existing != null
    val context = LocalContext.current

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var conditionGroups by remember {
        mutableStateOf(existing?.conditionGroups?.toMutableList() ?: mutableListOf(
            ConditionGroup(mutableListOf(ConditionDef(AtomicEventType.SHAKE)))
        ))
    }
    var timeWindowSec by remember { mutableFloatStateOf((existing?.timeWindowMs ?: 5000L) / 1000f) }
    var minInterval by remember { mutableFloatStateOf((existing?.minIntervalMinutes ?: 120).toFloat()) }
    var reactions by remember {
        mutableStateOf(existing?.reactions?.toMutableList() ?: mutableListOf(ReactionItem()))
    }
    var notif by remember { mutableStateOf(existing?.notification?.enabled ?: false) }
    var headsUp by remember { mutableStateOf(existing?.notification?.headsUp ?: false) }
    var playAudio by remember { mutableStateOf(existing?.notification?.playFeedbackAudio ?: false) }
    var vibrate by remember { mutableStateOf(existing?.notification?.vibration ?: false) }
    var lockScreen by remember { mutableStateOf(existing?.notification?.lockScreen ?: false) }

    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null && reactions.isNotEmpty()) {
            try {
                val input = context.contentResolver.openInputStream(uri)
                val file = java.io.File(context.filesDir, "audio_${System.currentTimeMillis()}.audio")
                input?.use { it.copyTo(file.outputStream()) }
                val idx = reactions.lastIndex
                reactions = reactions.toMutableList().also { it[idx] = it[idx].copy(audioPath = file.absolutePath) }
            } catch (_: Exception) {}
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (isEditing) "编辑事件" else "新建事件", fontWeight = FontWeight.Bold) },
            navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
            actions = {
                TextButton(onClick = {
                    onSave(EventDefinition(
                        id = existing?.id ?: "", name = name.ifBlank { "未命名事件" },
                        enabled = existing?.enabled ?: false, isPreset = false,
                        triggerType = if (conditionGroups.flatMap { it.conditions }.size > 1)
                            TriggerType.COMBINATION else TriggerType.SINGLE,
                        conditionGroups = conditionGroups.filter { it.conditions.isNotEmpty() },
                        timeWindowMs = (timeWindowSec * 1000).toLong(),
                        reactions = reactions.filter { it.text.isNotBlank() || it.audioPath.isNotBlank() },
                        notification = NotificationConfig(notif, headsUp, playAudio, vibrate, lockScreen),
                        minIntervalMinutes = minInterval.toInt()
                    ))
                }) { Text("保存", color = MaterialTheme.colorScheme.primary) }
            })
    }, modifier = modifier.fillMaxSize()) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(12.dp))

            // 1. 事件名称
            OutlinedTextField(name, { name = it }, label = { Text("事件名称") },
                placeholder = { Text("例：睡觉提醒") }, singleLine = true,
                modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(16.dp)); HorizontalDivider(); Spacer(Modifier.height(12.dp))

            // 2. 触发条件
            Text("触发条件", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            conditionGroups.forEachIndexed { gi, group ->
                if (gi > 0) {
                    Text("—— 或 ——", Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                group.conditions.forEachIndexed { ci, cond ->
                    if (ci > 0) {
                        Text("且", Modifier.padding(vertical = 2.dp), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary)
                    }
                    val label = "${gi + 1}${('A' + ci)}"
                    ConditionEditorCard(cond, label, { updated ->
                        conditionGroups = conditionGroups.toMutableList().also {
                            val g = it[gi].conditions.toMutableList(); g[ci] = updated; it[gi] = ConditionGroup(g)
                        }
                    }, {
                        if (group.conditions.size > 1) {
                            conditionGroups = conditionGroups.toMutableList().also {
                                val g = it[gi].conditions.toMutableList(); g.removeAt(ci); it[gi] = ConditionGroup(g)
                            }
                        } else if (conditionGroups.size > 1) {
                            conditionGroups = conditionGroups.toMutableList().also { it.removeAt(gi) }
                        }
                    }, group.conditions.size > 1 || conditionGroups.size > 1)
                    Spacer(Modifier.height(6.dp))
                }
                TextButton(onClick = {
                    conditionGroups = conditionGroups.toMutableList().also {
                        val g = it[gi].conditions.toMutableList(); g.add(ConditionDef(AtomicEventType.SHAKE))
                        it[gi] = ConditionGroup(g)
                    }
                }) { Text("+ 添加且条件") }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                conditionGroups = conditionGroups.toMutableList().also {
                    it.add(ConditionGroup(mutableListOf(ConditionDef(AtomicEventType.SHAKE))))
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("+ 添加或条件组") }

            Spacer(Modifier.height(16.dp)); HorizontalDivider(); Spacer(Modifier.height(12.dp))

            // 3. 时间窗口
            Text("时间窗口", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("${(timeWindowSec * 1000).toInt()}ms内满足条件则触发",
                style = MaterialTheme.typography.bodyMedium)
            Slider(timeWindowSec, { v -> timeWindowSec = v }, valueRange = 0.1f..20f, steps = 198, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(16.dp)); HorizontalDivider(); Spacer(Modifier.height(12.dp))

            // 4. 最小触发时间间隔
            Text("最小触发时间间隔", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("${minInterval.toInt()}分钟", style = MaterialTheme.typography.bodyMedium)
            Slider(minInterval, { v -> minInterval = v }, valueRange = 0f..360f, steps = 71, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(16.dp)); HorizontalDivider(); Spacer(Modifier.height(12.dp))

            // 5. 反馈
            Text("反馈", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            reactions.forEachIndexed { idx, item ->
                ReactionEditCard(idx, item, { updated -> reactions = reactions.toMutableList().also { it[idx] = updated } }, {
                    if (reactions.size > 1) reactions = reactions.toMutableList().also { it.removeAt(idx) }
                }, { audioPicker.launch("audio/*") }, reactions.size > 1)
                Spacer(Modifier.height(6.dp))
            }
            TextButton(onClick = { reactions = reactions.toMutableList().also { it.add(ReactionItem()) } }) {
                Text("+ 添加反馈项")
            }

            Spacer(Modifier.height(16.dp)); HorizontalDivider(); Spacer(Modifier.height(12.dp))

            // 6. 通知
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
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ConditionEditorCard(
    cond: ConditionDef, label: String, onUpdate: (ConditionDef) -> Unit,
    onRemove: () -> Unit, canRemove: Boolean
) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(label, Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.width(8.dp))
                var typeExpanded by remember { mutableStateOf(false) }
                Box(Modifier.weight(1f)) {
                    OutlinedButton(onClick = { typeExpanded = true }, Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
                        Text(cond.atomicType.displayName, maxLines = 1, style = MaterialTheme.typography.bodySmall)
                    }
                    DropdownMenu(typeExpanded, { typeExpanded = false }) {
                        AtomicEventType.entries.forEach { type ->
                            DropdownMenuItem(text = {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(type.displayName, style = MaterialTheme.typography.bodyMedium)
                                        if (type.requiresAccessibility) {
                                            Spacer(Modifier.width(4.dp))
                                            Text("⚠", style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                    Text(if (type.requiresAccessibility) "需在 设置→无障碍 中手动开启"
                                        else type.description, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }, onClick = {
                                val isState = type in listOf(AtomicEventType.SCREEN_ON, AtomicEventType.SCREEN_OFF,
                                    AtomicEventType.CHARGE_START, AtomicEventType.CHARGE_STOP)
                                onUpdate(when {
                                    type.supportsTimeRange -> cond.copy(atomicType = type, valueMin = 22, valueMax = 6)
                                    type.hasValue -> cond.copy(atomicType = type,
                                        operator = ConditionDef.CompareOp.LESS_THAN, value = cond.value ?: 50)
                                    type == AtomicEventType.LONG_PRESS -> cond.copy(atomicType = type, value = 2000)
                                    else -> cond.copy(atomicType = type, checkCurrentState = isState)
                                })
                                typeExpanded = false
                                if (type.requiresAccessibility)
                                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            })
                        }
                    }
                }
                if (canRemove)
                    TextButton(onClick = onRemove, contentPadding = PaddingValues(4.dp)) {
                        Text("✕", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
            }

            val showValue = cond.atomicType.hasValue || cond.atomicType == AtomicEventType.LONG_PRESS
            val showCount = cond.atomicType.supportsCount && cond.atomicType != AtomicEventType.LONG_PRESS
            val showTimeRange = cond.atomicType.supportsTimeRange
            val isState = cond.atomicType in listOf(AtomicEventType.SCREEN_ON, AtomicEventType.SCREEN_OFF,
                AtomicEventType.CHARGE_START, AtomicEventType.CHARGE_STOP)

            if (showCount) {
                Spacer(Modifier.height(4.dp))
                var ct by remember(cond.count) { mutableStateOf(cond.count.toString()) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("次数:", style = MaterialTheme.typography.bodySmall); Spacer(Modifier.width(8.dp))
                    OutlinedTextField(ct, { s ->
                        val f = s.filter { it.isDigit() }; ct = f
                        f.toIntOrNull()?.coerceIn(1, 99)?.let { onUpdate(cond.copy(count = it)) }
                    }, Modifier.width(60.dp), singleLine = true, textStyle = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(8.dp))
                    val detectMs = cond.value ?: 3000
                    Text("检测时间: ${detectMs}ms", style = MaterialTheme.typography.bodySmall)
                    Slider(detectMs.toFloat(), { v -> onUpdate(cond.copy(value = v.toInt())) },
                        valueRange = 100f..10000f, steps = 98, modifier = Modifier.weight(1f))
                }
            }

            if (showTimeRange) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("时段: ", style = MaterialTheme.typography.bodySmall)
                    HourPicker(cond.valueMin ?: 22) { onUpdate(cond.copy(valueMin = it)) }
                    Text(" :00 — ", style = MaterialTheme.typography.bodySmall)
                    HourPicker(cond.valueMax ?: 6) { onUpdate(cond.copy(valueMax = it)) }
                    Text(" :00", style = MaterialTheme.typography.bodySmall)
                }
            }

            if (showValue && !showCount) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val unit = when (cond.atomicType) {
                        AtomicEventType.BATTERY_LEVEL -> "%"
                        AtomicEventType.LONG_USAGE -> "分钟"
                        AtomicEventType.LONG_PRESS -> "ms"
                        else -> ""
                    }
                    Text("值:", style = MaterialTheme.typography.bodySmall); Spacer(Modifier.width(8.dp))
                    var vt by remember(cond.value ?: 0) { mutableStateOf((cond.value ?: 0).toString()) }
                    OutlinedTextField(vt, { s ->
                        val f = s.filter { it.isDigit() }; vt = f
                        f.toIntOrNull()?.let { onUpdate(cond.copy(value = it)) }
                    }, Modifier.width(64.dp), singleLine = true, textStyle = MaterialTheme.typography.bodySmall)
                    Text(unit, style = MaterialTheme.typography.bodySmall)
                }
                val rng = when (cond.atomicType) {
                    AtomicEventType.LONG_USAGE -> 1f..1439f; AtomicEventType.BATTERY_LEVEL -> 1f..99f
                    AtomicEventType.LONG_PRESS -> 1000f..5000f; else -> 0f..100f
                }
                Slider((cond.value ?: 50).toFloat(), { v -> onUpdate(cond.copy(value = v.toInt())) },
                    valueRange = rng, steps = rng.endInclusive.toInt() - rng.start.toInt() - 1,
                    modifier = Modifier.fillMaxWidth())
            }

            if (isState) {
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(cond.checkCurrentState, { onUpdate(cond.copy(checkCurrentState = it)) })
                    Text("查询当前状态", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ReactionEditCard(
    idx: Int, item: ReactionItem, onUpdate: (ReactionItem) -> Unit,
    onRemove: () -> Unit, onPickAudio: () -> Unit, canRemove: Boolean
) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("反馈 #${idx + 1}", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold)
                if (canRemove) TextButton(onClick = onRemove) {
                    Text("删除", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall) }
            }
            OutlinedTextField(item.text, { onUpdate(item.copy(text = it)) },
                label = { Text("反馈文本") }, placeholder = { Text("触发时显示的文字") },
                singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(item.audioPath, { onUpdate(item.copy(audioPath = it)) },
                    label = { Text("音频文件") }, placeholder = { Text("上传或输入路径") },
                    singleLine = true, modifier = Modifier.weight(1f), textStyle = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(4.dp))
                OutlinedButton(onClick = onPickAudio,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                    Text("选择", style = MaterialTheme.typography.labelSmall) }
            }
        }
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

@Composable
private fun HourPicker(value: Int, onValueChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, contentPadding = PaddingValues(horizontal = 6.dp)) {
            Text(value.toString().padStart(2, '0'), style = MaterialTheme.typography.bodySmall) }
        DropdownMenu(expanded, { expanded = false }) {
            (0..23).forEach { h ->
                DropdownMenuItem(text = { Text("${h.toString().padStart(2, '0')}:00") },
                    onClick = { onValueChange(h); expanded = false })
            }
        }
    }
}
