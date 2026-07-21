package com.example.pat.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pat.data.PresetRepository
import com.example.pat.event.AtomicEventType
import com.example.pat.model.ConditionClause
import com.example.pat.model.ConditionOperator
import com.example.pat.model.UserRule

/**
 * 规则构建器 —— 创建/编辑用户自定义事件规则。
 */
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

    // ── 编辑状态 ──
    var name by remember { mutableStateOf(existingRule?.name ?: "") }
    var conditions by remember {
        mutableStateOf(
            existingRule?.conditions?.toMutableList()
                ?: mutableListOf(ConditionClause(AtomicEventType.SHAKE))
        )
    }
    var operator by remember {
        mutableStateOf(existingRule?.operator ?: ConditionOperator.AND)
    }
    var timeWindowSec by remember {
        mutableFloatStateOf(((existingRule?.timeWindowMs ?: 10_000L) / 1000f))
    }
    var priority by remember {
        mutableFloatStateOf((existingRule?.priority ?: 5).toFloat())
    }
    var minIntervalMinutes by remember {
        mutableFloatStateOf((existingRule?.minIntervalMinutes ?: 10).toFloat())
    }
    var reactionText by remember { mutableStateOf(existingRule?.reactionText ?: "") }
    var reactionAudioPath by remember { mutableStateOf(existingRule?.reactionAudioPath ?: "") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // ── 标题栏 ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("< 返回") }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (isEditing) "编辑规则" else "创建新规则",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── 规则名称 ──
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("规则名称") },
            placeholder = { Text("例如：睡觉提醒") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ── 触发条件 ──
        Text("触发条件", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))

        conditions.forEachIndexed { index, clause ->
            ConditionRow(
                clause = clause,
                onUpdate = { updated -> conditions = conditions.toMutableList().also { it[index] = updated } },
                onRemove = {
                    if (conditions.size > 1) {
                        conditions = conditions.toMutableList().also { it.removeAt(index) }
                    }
                },
                canRemove = conditions.size > 1
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        TextButton(onClick = {
            conditions = conditions.toMutableList().also { it.add(ConditionClause(AtomicEventType.SHAKE)) }
        }) {
            Text("+ 添加条件")
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── 组合方式 ──
        Text("组合方式", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))

        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ConditionOperator.entries.forEach { op ->
                FilterChip(
                    selected = operator == op,
                    onClick = { operator = op },
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

        Spacer(modifier = Modifier.height(12.dp))

        // ── 时间窗口 ──
        Text("时间窗口: ${timeWindowSec.toInt()} 秒内完成",
            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Slider(
            value = timeWindowSec,
            onValueChange = { timeWindowSec = it },
            valueRange = 1f..120f,
            steps = 23,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ── 优先级 ──
        Text("优先级: ${priority.toInt()}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Slider(
            value = priority,
            onValueChange = { priority = it },
            valueRange = 1f..10f,
            steps = 8,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ── 冷却时间 ──
        Text("最小触发间隔: ${minIntervalMinutes.toInt()} 分钟",
            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Slider(
            value = minIntervalMinutes,
            onValueChange = { minIntervalMinutes = it },
            valueRange = 0f..60f,
            steps = 11,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ── 反馈设置 ──
        Text("反馈设置", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))

        OutlinedTextField(
            value = reactionText,
            onValueChange = { reactionText = it },
            label = { Text("反馈文本") },
            placeholder = { Text("事件触发时显示的文字") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 音频选择：内置预设列表
        val presets = remember { presetRepository.getAll() }
        var audioExpanded by remember { mutableStateOf(false) }
        val selectedPreset = remember(reactionAudioPath) {
            if (reactionAudioPath.isBlank()) null
            else presets.find { it.audioAssetPath == reactionAudioPath }
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { audioExpanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (selectedPreset != null) "音频: ${selectedPreset.name}"
                    else if (reactionAudioPath.isNotBlank()) "音频: 自定义文件"
                    else "选择音频（可选）",
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            DropdownMenu(
                expanded = audioExpanded,
                onDismissRequest = { audioExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("无音频", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    onClick = {
                        reactionAudioPath = ""
                        audioExpanded = false
                    }
                )
                presets.forEach { preset ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(preset.name, style = MaterialTheme.typography.bodyMedium)
                                Text(preset.text, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (preset.audioAssetPath.isNotBlank()) {
                                    Text("🎵 ${preset.audioAssetPath}", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        },
                        onClick = {
                            reactionAudioPath = preset.audioAssetPath
                            if (reactionText.isBlank()) reactionText = preset.text
                            audioExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── 保存按钮 ──
        Button(
            onClick = {
                val rule = UserRule(
                    id = existingRule?.id ?: "",
                    name = name.ifBlank { "未命名规则" },
                    conditions = conditions,
                    operator = operator,
                    timeWindowMs = (timeWindowSec * 1000).toLong(),
                    priority = priority.toInt(),
                    enabled = existingRule?.enabled ?: true,
                    reactionText = reactionText,
                    reactionAudioPath = reactionAudioPath,
                    minIntervalMinutes = minIntervalMinutes.toInt()
                )
                onSave(rule)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = name.isNotBlank() && conditions.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("保存规则", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 4.dp))
        }

        // 底部留白，防止保存按钮被导航栏遮挡
        Spacer(modifier = Modifier.height(32.dp))
    }
}

/**
 * 单个条件编辑行。紧凑布局适配小屏手机。
 */
@Composable
private fun ConditionRow(
    clause: ConditionClause,
    onUpdate: (ConditionClause) -> Unit,
    onRemove: () -> Unit,
    canRemove: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 事件类型选择
                var typeExpanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { typeExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(clause.eventType.displayName, maxLines = 1,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    DropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false }
                    ) {
                        AtomicEventType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(type.displayName, style = MaterialTheme.typography.bodyMedium)
                                        Text(type.description, style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                onClick = {
                                    onUpdate(clause.copy(
                                        eventType = type,
                                        operator = if (type.hasValue) ConditionClause.CompareOp.LESS_THAN else null,
                                        value = if (type.hasValue) clause.value else null
                                    ))
                                    typeExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // 次数
                OutlinedTextField(
                    value = clause.count.toString(),
                    onValueChange = { s ->
                        val c = s.filter { it.isDigit() }.toIntOrNull() ?: 1
                        onUpdate(clause.copy(count = c.coerceIn(1, 99)))
                    },
                    modifier = Modifier.width(48.dp),
                    singleLine = true,
                    label = { Text("次", style = MaterialTheme.typography.labelSmall) },
                    textStyle = MaterialTheme.typography.bodySmall
                )

                // 删除
                if (canRemove) {
                    IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                        Text("✕", style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    Spacer(modifier = Modifier.width(32.dp))
                }
            }

            // 数值条件（仅 hasValue 事件显示）
            if (clause.eventType.hasValue) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 比较符
                    var opExpanded by remember { mutableStateOf(false) }
                    Box {
                        TextButton(
                            onClick = { opExpanded = true },
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text(clause.operator?.symbol ?: "<")
                        }
                        DropdownMenu(
                            expanded = opExpanded,
                            onDismissRequest = { opExpanded = false }
                        ) {
                            ConditionClause.CompareOp.entries.forEach { op ->
                                DropdownMenuItem(
                                    text = { Text(op.symbol) },
                                    onClick = {
                                        onUpdate(clause.copy(operator = op))
                                        opExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // 阈值
                    OutlinedTextField(
                        value = clause.value?.toString() ?: "",
                        onValueChange = { s ->
                            val v = s.filter { it.isDigit() }.toIntOrNull()
                            onUpdate(clause.copy(value = v))
                        },
                        modifier = Modifier.width(64.dp),
                        singleLine = true,
                        label = { Text("值", style = MaterialTheme.typography.labelSmall) },
                        textStyle = MaterialTheme.typography.bodySmall
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = when (clause.eventType) {
                            AtomicEventType.BATTERY_LEVEL -> "%"
                            AtomicEventType.LONG_USAGE -> "分钟"
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
