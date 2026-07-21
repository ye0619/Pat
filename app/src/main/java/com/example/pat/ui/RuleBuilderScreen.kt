package com.example.pat.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pat.data.PresetRepository
import com.example.pat.event.AtomicEventType
import com.example.pat.model.ConditionClause
import com.example.pat.model.ConditionOperator
import com.example.pat.model.UserRule

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RuleBuilderScreen(
    existingRule: UserRule?,
    presetRepository: PresetRepository,
    onSave: (UserRule) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isEditing = existingRule != null
    val context = LocalContext.current

    var name by remember { mutableStateOf(existingRule?.name ?: "") }
    var conditions by remember {
        mutableStateOf(
            existingRule?.conditions?.toMutableList()
                ?: mutableListOf(ConditionClause(AtomicEventType.SHAKE))
        )
    }
    var operator by remember { mutableStateOf(existingRule?.operator ?: ConditionOperator.AND) }
    var timeWindowSec by remember { mutableFloatStateOf(((existingRule?.timeWindowMs ?: 10_000L) / 1000f)) }
    var priority by remember { mutableFloatStateOf((existingRule?.priority ?: 5).toFloat()) }
    var minIntervalMinutes by remember { mutableFloatStateOf((existingRule?.minIntervalMinutes ?: 10).toFloat()) }
    var reactionText by remember { mutableStateOf(existingRule?.reactionText ?: "") }
    var reactionAudioPath by remember { mutableStateOf(existingRule?.reactionAudioPath ?: "") }

    // 文件选择器
    val audioFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            // 复制到应用内部存储
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val fileName = "custom_audio_${System.currentTimeMillis()}.audio"
                val outputFile = java.io.File(context.filesDir, fileName)
                inputStream?.use { input ->
                    outputFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                reactionAudioPath = outputFile.absolutePath
            } catch (e: Exception) {
                // 失败时保留原路径
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("< 返回") }
            Spacer(Modifier.width(4.dp))
            Text(
                if (isEditing) "编辑规则" else "创建新规则",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { Text("规则名称") }, placeholder = { Text("例如：睡觉提醒") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        Text("触发条件", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))

        conditions.forEachIndexed { index, clause ->
            ConditionRow(
                clause = clause,
                onUpdate = { updated -> conditions = conditions.toMutableList().also { it[index] = updated } },
                onRemove = {
                    if (conditions.size > 1)
                        conditions = conditions.toMutableList().also { it.removeAt(index) }
                },
                canRemove = conditions.size > 1
            )
            Spacer(Modifier.height(4.dp))
        }

        TextButton(onClick = {
            conditions = conditions.toMutableList().also { it.add(ConditionClause(AtomicEventType.SHAKE)) }
        }) { Text("+ 添加条件") }

        Spacer(Modifier.height(12.dp))

        Text("组合方式", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ConditionOperator.entries.forEach { op ->
                FilterChip(
                    selected = operator == op, onClick = { operator = op },
                    label = {
                        Text(
                            when (op) {
                                ConditionOperator.AND -> "全部(AND)"
                                ConditionOperator.OR -> "任意(OR)"
                                ConditionOperator.SEQUENCE -> "顺序(SEQ)"
                            },
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("时间窗口: ${timeWindowSec.toInt()} 秒内", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Slider(value = timeWindowSec, onValueChange = { timeWindowSec = it },
            valueRange = 1f..120f, steps = 23, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(8.dp))
        Text("优先级: ${priority.toInt()}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Slider(value = priority, onValueChange = { priority = it },
            valueRange = 1f..10f, steps = 8, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(8.dp))
        Text("冷却: ${minIntervalMinutes.toInt()} 分钟", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Slider(value = minIntervalMinutes, onValueChange = { minIntervalMinutes = it },
            valueRange = 0f..60f, steps = 11, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(12.dp))

        // ── 反馈设置 ──
        Text("反馈设置", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))

        OutlinedTextField(
            value = reactionText, onValueChange = { reactionText = it },
            label = { Text("反馈文本") }, placeholder = { Text("事件触发时显示的文字") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        // 音频：上传文件 + 预设选择
        Text("反馈音频", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(2.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { audioFilePicker.launch("audio/*") },
                modifier = Modifier.weight(1f)
            ) { Text("选择音频文件", style = MaterialTheme.typography.labelMedium) }

            var presetExpanded by remember { mutableStateOf(false) }
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { presetExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("内置预设", style = MaterialTheme.typography.labelMedium) }
                DropdownMenu(presetExpanded, { presetExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("无音频") },
                        onClick = { reactionAudioPath = ""; presetExpanded = false }
                    )
                    presetRepository.getAll().forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            onClick = {
                                reactionAudioPath = p.audioAssetPath
                                if (reactionText.isBlank()) reactionText = p.text
                                presetExpanded = false
                            }
                        )
                    }
                }
            }
        }
        if (reactionAudioPath.isNotBlank()) {
            Text(
                text = "已选: ${reactionAudioPath.takeLast(40)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                onSave(UserRule(
                    id = existingRule?.id ?: "", name = name.ifBlank { "未命名规则" },
                    conditions = conditions, operator = operator,
                    timeWindowMs = (timeWindowSec * 1000).toLong(), priority = priority.toInt(),
                    enabled = existingRule?.enabled ?: true,
                    reactionText = reactionText, reactionAudioPath = reactionAudioPath,
                    minIntervalMinutes = minIntervalMinutes.toInt()
                ))
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = name.isNotBlank() && conditions.isNotEmpty()
        ) { Text("保存规则", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 4.dp)) }

        Spacer(Modifier.height(32.dp))
    }
}

/** 单个条件编辑行 */
@Composable
private fun ConditionRow(
    clause: ConditionClause,
    onUpdate: (ConditionClause) -> Unit,
    onRemove: () -> Unit,
    canRemove: Boolean
) {
    val showCount = clause.eventType.supportsCount
    val showTimeRange = clause.eventType.supportsTimeRange
    val showValue = clause.eventType.hasValue

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                var typeExpanded by remember { mutableStateOf(false) }
                Box(Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { typeExpanded = true }, modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) { Text(clause.eventType.displayName, maxLines = 1, style = MaterialTheme.typography.bodySmall) }
                    DropdownMenu(typeExpanded, { typeExpanded = false }) {
                        // 过滤：自定义规则中不显示 IMPACT（由 CLICK 替代）
                        AtomicEventType.entries
                            .filter { it != AtomicEventType.IMPACT }
                            .forEach { type ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(type.displayName, style = MaterialTheme.typography.bodyMedium)
                                            if (type.requiresAccessibility) {
                                                Spacer(Modifier.width(4.dp))
                                                Text("不推荐", style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                        Text(
                                            if (type.requiresAccessibility)
                                                "需在 设置→无障碍 中手动开启，影响性能"
                                            else type.description,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    onUpdate(
                                        if (type.supportsTimeRange)
                                            clause.copy(eventType = type, valueMin = 22, valueMax = 6)
                                        else if (type.hasValue)
                                            clause.copy(eventType = type, operator = ConditionClause.CompareOp.LESS_THAN, value = clause.value ?: 50)
                                        else
                                            clause.copy(eventType = type)
                                    )
                                    typeExpanded = false
                                }
                            )
                        }
                    }
                }

                // 次数输入（仅计数事件）
                if (showCount) {
                    Spacer(Modifier.width(4.dp))
                    var countText by remember(clause.count) { mutableStateOf(clause.count.toString()) }
                    var countError by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        value = countText,
                        onValueChange = { s ->
                            val filtered = s.filter { it.isDigit() }
                            if (filtered.isEmpty()) {
                                countText = s
                                countError = true
                            } else {
                                val v = filtered.toIntOrNull()?.coerceIn(1, 99) ?: 1
                                countText = v.toString()
                                countError = false
                                onUpdate(clause.copy(count = v))
                            }
                        },
                        isError = countError,
                        supportingText = if (countError) {{ Text("请输入数字") }} else null,
                        modifier = Modifier.width(56.dp), singleLine = true,
                        label = { Text("次", style = MaterialTheme.typography.labelSmall) },
                        textStyle = MaterialTheme.typography.bodySmall,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                if (canRemove) {
                    IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                        Text("✕", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // 时间段选择
            if (showTimeRange) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("时段: ", style = MaterialTheme.typography.bodySmall)
                    HourPicker(
                        value = clause.valueMin ?: 22,
                        onValueChange = { onUpdate(clause.copy(valueMin = it)) }
                    )
                    Text(" :00 — ", style = MaterialTheme.typography.bodySmall)
                    HourPicker(
                        value = clause.valueMax ?: 6,
                        onValueChange = { onUpdate(clause.copy(valueMax = it)) }
                    )
                    Text(" :00", style = MaterialTheme.typography.bodySmall)
                }
            }

            // 数值条件
            if (showValue) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    var opExpanded by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { opExpanded = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text(clause.operator?.symbol ?: "<")
                        }
                        DropdownMenu(opExpanded, { opExpanded = false }) {
                            ConditionClause.CompareOp.entries.forEach { op ->
                                DropdownMenuItem(text = { Text(op.symbol) }, onClick = {
                                    onUpdate(clause.copy(operator = op)); opExpanded = false
                                })
                            }
                        }
                    }
                    OutlinedTextField(
                        value = clause.value?.toString() ?: "",
                        onValueChange = { s ->
                            val v = s.filter { it.isDigit() }.toIntOrNull()
                            onUpdate(clause.copy(value = v))
                        },
                        modifier = Modifier.width(64.dp), singleLine = true,
                        label = { Text("值", style = MaterialTheme.typography.labelSmall) },
                        textStyle = MaterialTheme.typography.bodySmall,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Text(
                        when (clause.eventType) {
                            AtomicEventType.BATTERY_LEVEL -> " %"
                            AtomicEventType.LONG_USAGE -> " 分钟"
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** 小时选择器（0-23） */
@Composable
private fun HourPicker(value: Int, onValueChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, contentPadding = PaddingValues(horizontal = 6.dp)) {
            Text(value.toString().padStart(2, '0'), style = MaterialTheme.typography.bodySmall)
        }
        DropdownMenu(expanded, { expanded = false }) {
            (0..23).forEach { h ->
                DropdownMenuItem(
                    text = { Text(h.toString().padStart(2, '0') + ":00") },
                    onClick = { onValueChange(h); expanded = false }
                )
            }
        }
    }
}
