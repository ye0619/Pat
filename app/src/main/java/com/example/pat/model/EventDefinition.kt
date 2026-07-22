package com.example.pat.model

import com.example.pat.event.AtomicEventType
import com.example.pat.event.EventType

/**
 * 统一事件定义。自定义事件条件为平铺列表（全部 AND）。
 * 自定义事件可选条件排除预设事件已使用的类型。
 */
data class EventDefinition(
    val id: String = "",
    val name: String,
    val enabled: Boolean = false,
    val isPreset: Boolean = false,
    val eventType: EventType? = null,
    val conditions: List<ConditionDef> = emptyList(),
    val timeWindowMs: Long = 5000L,
    val reactions: List<ReactionItem> = emptyList(),
    val notification: NotificationConfig = NotificationConfig(),
    val minIntervalMinutes: Int = 120
) {
    val conditionSummary: String
        get() = if (conditions.isEmpty()) "无条件"
        else conditions.joinToString("、") { it.displayText }

    companion object {
        /** 预设事件占用的条件类型，自定义事件不可选用 */
        val PRESET_CONDITION_TYPES: Set<AtomicEventType> = setOf(
            AtomicEventType.SHAKE,
            AtomicEventType.DROP,
            AtomicEventType.CHARGE_START,
            AtomicEventType.BATTERY_LEVEL,
            AtomicEventType.LONG_USAGE
        )

        /** 自定义事件可选的条件类型 */
        val AVAILABLE_CONDITION_TYPES: List<AtomicEventType> =
            AtomicEventType.entries.filter { it !in PRESET_CONDITION_TYPES }

        fun fromEventConfig(config: EventConfig, reactions: List<ReactionItem>): EventDefinition {
            val condition = ConditionDef.fromEventType(config.eventType, config.threshold)
            return EventDefinition(
                id = "preset_${config.eventType.name}",
                name = EventConfig.displayName(config.eventType),
                enabled = config.enabled,
                isPreset = true,
                eventType = config.eventType,
                conditions = listOf(condition),
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

/**
 * 单个条件定义。
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
                operator = CompareOp.GREATER_EQUAL, value = threshold
            )
            EventType.CHARGE_START -> ConditionDef(atomicType = AtomicEventType.CHARGE_START)
            EventType.LOW_BATTERY -> ConditionDef(
                atomicType = AtomicEventType.BATTERY_LEVEL,
                operator = CompareOp.LESS_EQUAL, value = threshold
            )
            EventType.SHAKE -> ConditionDef(atomicType = AtomicEventType.SHAKE)
            EventType.DROP -> ConditionDef(atomicType = AtomicEventType.DROP)
        }
    }
}

data class NotificationConfig(
    val enabled: Boolean = false,
    val headsUp: Boolean = false,
    val playFeedbackAudio: Boolean = false,
    val vibration: Boolean = false,
    val lockScreen: Boolean = false
)
