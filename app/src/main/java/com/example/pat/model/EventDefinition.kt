package com.example.pat.model

import com.example.pat.event.AtomicEventType
import com.example.pat.event.EventType

/**
 * 统一事件定义 —— 替代旧的 UserRule + ConditionClause 体系。
 *
 * 预设事件（isPreset=true）由系统内置，用户不可删除，仅可编辑反馈和通知。
 * 自定义事件（isPreset=false）由用户创建，支持多条件组合。
 *
 * 条件组合逻辑（支持未来扩展为逻辑树）：
 * - [conditionGroups] 是 OR 关系：任意一个 ConditionGroup 满足即触发
 * - 每个 [ConditionGroup] 内部是 AND 关系：所有条件必须同时满足
 */
data class EventDefinition(
    val id: String = "",
    val name: String,
    val enabled: Boolean = false,
    val isPreset: Boolean = false,
    val eventType: EventType? = null,
    val triggerType: TriggerType = TriggerType.SINGLE,
    val conditionGroups: List<ConditionGroup> = emptyList(),
    val timeWindowMs: Long = 5000L,
    val reactions: List<ReactionItem> = emptyList(),
    val notification: NotificationConfig = NotificationConfig(),
    val minIntervalMinutes: Int = 120
) {
    val conditionSummary: String
        get() {
            if (conditionGroups.isEmpty()) return "无条件"
            return conditionGroups.joinToString(" 或 ") { group ->
                if (group.conditions.size == 1) {
                    group.conditions.first().displayText
                } else {
                    group.conditions.joinToString(" 且 ") { it.displayText }
                }
            }
        }

    companion object {
        fun fromEventConfig(config: EventConfig, reactions: List<ReactionItem>): EventDefinition {
            val condition = ConditionDef.fromEventType(config.eventType, config.threshold)
            return EventDefinition(
                id = "preset_${config.eventType.name}",
                name = EventConfig.displayName(config.eventType),
                enabled = config.enabled,
                isPreset = true,
                eventType = config.eventType,
                triggerType = TriggerType.SINGLE,
                conditionGroups = listOf(ConditionGroup(listOf(condition))),
                reactions = reactions.ifEmpty {
                    listOf(ReactionItem(text = config.customText, audioPath = config.customAudioPath))
                },
                notification = NotificationConfig(
                    enabled = config.notificationEnabled,
                    headsUp = config.showHeadsUp,
                    playFeedbackAudio = config.soundEnabled,
                    vibration = config.vibrationEnabled,
                    lockScreen = config.lockScreenPublic
                ),
                minIntervalMinutes = config.minIntervalMinutes
            )
        }
    }
}

enum class TriggerType { SINGLE, COMBINATION }

/**
 * 条件组 —— 组内条件为 AND，组间为 OR。
 * 未来可扩展为 sealed class LogicNode 支持嵌套 AND/OR/NOT。
 */
data class ConditionGroup(
    val conditions: List<ConditionDef> = emptyList()
)

/**
 * 单个条件定义 —— 替代旧的 ConditionClause。
 */
data class ConditionDef(
    val atomicType: AtomicEventType,
    val operator: CompareOp? = null,
    val value: Int? = null,
    val count: Int = 1,
    val valueMin: Int? = null,
    val valueMax: Int? = null,
    val checkCurrentState: Boolean = false
) {
    enum class CompareOp(val symbol: String) {
        LESS_THAN("<"), LESS_EQUAL("<="), GREATER_THAN(">"),
        GREATER_EQUAL(">="), EQUAL("=")
    }

    val displayText: String
        get() = when {
            atomicType.supportsTimeRange && valueMin != null && valueMax != null ->
                "${atomicType.displayName} ${valueMin}:00-${valueMax}:00"
            atomicType.hasValue && operator != null && value != null ->
                "${atomicType.displayName} ${operator.symbol} $value"
            atomicType.supportsCount && count > 1 ->
                "${atomicType.displayName} ${count}次"
            else -> atomicType.displayName
        }

    companion object {
        fun fromEventType(eventType: EventType, threshold: Int): ConditionDef = when (eventType) {
            EventType.SCREEN_LONG_USAGE -> ConditionDef(
                atomicType = AtomicEventType.LONG_USAGE,
                operator = CompareOp.GREATER_EQUAL,
                value = threshold
            )
            EventType.CHARGE_START -> ConditionDef(atomicType = AtomicEventType.CHARGE_START)
            EventType.LOW_BATTERY -> ConditionDef(
                atomicType = AtomicEventType.BATTERY_LEVEL,
                operator = CompareOp.LESS_EQUAL,
                value = threshold
            )
            EventType.SHAKE -> ConditionDef(atomicType = AtomicEventType.SHAKE)
            EventType.DROP -> ConditionDef(atomicType = AtomicEventType.DROP)
        }
    }
}

/**
 * 通知配置 —— 内嵌在 EventDefinition 中。
 */
data class NotificationConfig(
    val enabled: Boolean = false,
    val headsUp: Boolean = false,
    val playFeedbackAudio: Boolean = false,
    val vibration: Boolean = false,
    val lockScreen: Boolean = false
)
