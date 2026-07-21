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
                text = if (isEditing) "编辑规则" else "创建新规则",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── 规则名称 ──
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("规则名称") },
            placeholder = { Text("例如：睡觉提醒") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ── 触发条件 ──
        Text(
            text = "触发条件",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

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

        Spacer(modifier = Modifier.height(4.dp))

        TextButton(onClick = {
            conditions = conditions.toMutableList().also { it.add(ConditionClause(AtomicEventType.SHAKE)) }
        }) {
            Text("+ 添加条件")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── 组合方式 ──
        Text(
            text = "组合方式",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ConditionOperator.entries.forEach { op ->
                FilterChip(
                    selected = operator == op,
                    onClick = { operator = op },
                    label = {
                        Text(when (op) {
                            ConditionOperator.AND -> "全部满足 (AND)"
                            ConditionOperator.OR -> "任意满足 (OR)"
                            ConditionOperator.SEQUENCE -> "按顺序 (SEQ)"
                        })
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── 时间窗口 ──
        Text(
            text = "时间窗口: ${timeWindowSec.toInt()} 秒",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Slider(
            value = timeWindowSec,
            onValueChange = { timeWindowSec = it },
            valueRange = 1f..120f,
            steps = 23,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ── 优先级 ──
        Text(
            text = "优先级: ${priority.toInt()}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Slider(
            value = priority,
            onValueChange = { priority = it },
            valueRange = 1f..10f,
            steps = 8,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ── 冷却时间 ──
        Text(
            text = "最小触发间隔: ${minIntervalMinutes.toInt()} 分钟",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Slider(
            value = minIntervalMinutes,
            onValueChange = { minIntervalMinutes = it },
            valueRange = 0f..60f,
            steps = 11,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(32.dp))

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
                    reactionPresetId = existingRule?.reactionPresetId ?: "",
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
    }
}

/**
 * 单个条件编辑行。
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
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 事件类型选择
                var expanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { expanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(clause.eventType.displayName, maxLines = 1)
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        AtomicEventType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text("${type.displayName} — ${type.description}") },
                                onClick = {
                                    onUpdate(clause.copy(
                                        eventType = type,
                                        operator = if (type.hasValue) ConditionClause.CompareOp.LESS_THAN else null,
                                        value = if (type.hasValue) clause.value else null
                                    ))
                                    expanded = false
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
                    modifier = Modifier.width(56.dp),
                    singleLine = true,
                    label = { Text("次") }
                )

                // 删除
                if (canRemove) {
                    TextButton(onClick = onRemove) { Text("✕") }
                }
            }

            // 数值条件（仅 hasValue 事件显示）
            if (clause.eventType.hasValue) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 比较符
                    var opExpanded by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { opExpanded = true }) {
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
                        modifier = Modifier.width(72.dp),
                        singleLine = true,
                        label = { Text("值") }
                    )

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
