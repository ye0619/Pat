package com.example.pat.engine

import android.util.Log
import com.example.pat.data.EventConfigRepository
import com.example.pat.data.EventDefinitionRepository
import com.example.pat.data.PresetRepository
import com.example.pat.event.AtomicEvent
import com.example.pat.event.AtomicEventBus
import com.example.pat.event.AtomicEventType
import com.example.pat.event.EventType
import com.example.pat.event.toType
import com.example.pat.model.ConditionDef
import com.example.pat.model.EventConfig
import com.example.pat.model.EventDefinition
import com.example.pat.model.NotificationConfig
import com.example.pat.model.ReactionItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 统一规则引擎 v3 —— 处理预设事件（EventConfig）和自定义事件（EventDefinition）。
 */
class RuleEngineV2(
    private val configRepository: EventConfigRepository,
    private val definitionRepository: EventDefinitionRepository,
    private val presetRepository: PresetRepository? = null,
    private val history: EventHistoryBuffer = EventHistoryBuffer(),
    private val scope: CoroutineScope,
    private val stateProvider: DeviceStateProvider? = null
) {
    private val evaluator = ConditionEvaluator(history, stateProvider)
    private val priorityResolver = PriorityResolver()

    var onRuleMatched: ((PriorityResolver.MatchedRule) -> Unit)? = null

    private var collectionJob: Job? = null

    @Volatile
    var lastMatchedRules: List<PriorityResolver.MatchedRule> = emptyList()
        private set

    @Volatile
    var todayTriggerCount: Int = 0
        private set

    private val _recentTriggers = mutableListOf<RecentTrigger>()
    val recentTriggers: List<RecentTrigger> get() = _recentTriggers.toList()

    data class RecentTrigger(
        val displayName: String,
        val displayText: String,
        val timestamp: Long
    )

    fun start() {
        collectionJob?.cancel()
        collectionJob = scope.launch {
            AtomicEventBus.events.collectLatest { event -> processEvent(event) }
        }
        Log.i(TAG, "Unified RuleEngineV2 started")
    }

    fun stop() {
        collectionJob?.cancel(); collectionJob = null
        Log.i(TAG, "RuleEngineV2 stopped")
    }

    fun reloadRules() { Log.d(TAG, "Rules will be reloaded on next event") }

    private fun processEvent(event: AtomicEvent) {
        val now = System.currentTimeMillis()
        history.record(event); history.prune(now)

        val atomicType = event.toType()
        val matchedBase = evaluateBaseEvent(atomicType, now)
        val matchedCustom = evaluateEventDefinitions(atomicType, now)

        val allMatched = matchedBase + matchedCustom
        if (allMatched.isEmpty()) return

        val toExecute = priorityResolver.resolve(allMatched, now)
        for (rule in toExecute) {
            priorityResolver.markExecuted(rule.ruleId, now)
            Log.i(TAG, "Rule matched: \"${rule.displayName}\" (priority=${rule.priority})")
            val reaction = rule.reactions.firstOrNull() ?: ReactionItem()
            todayTriggerCount++
            _recentTriggers.add(0, RecentTrigger(
                displayName = rule.displayName,
                displayText = reaction.text.ifBlank { "\"${rule.displayName}\" 已触发" },
                timestamp = now
            ))
            if (_recentTriggers.size > 20) _recentTriggers.removeAt(_recentTriggers.lastIndex)
            onRuleMatched?.invoke(rule)
        }
        lastMatchedRules = toExecute
    }

    private fun evaluateBaseEvent(atomicType: AtomicEventType, now: Long): List<PriorityResolver.MatchedRule> {
        val baseType = mapAtomicToEventType(atomicType) ?: return emptyList()
        val config = configRepository.getByEventType(baseType) ?: return emptyList()
        if (!config.enabled) return emptyList()

        val condition = buildBaseCondition(config)
        if (!evaluator.evaluate(condition, BASE_EVENT_WINDOW_MS, now)) return emptyList()

        // 解析预设获取反馈内容（文本+音频）
        val preset = config.presetId.let { id ->
            if (id.isNotBlank()) presetRepository?.getById(id) else null
        }
        val reactions = if (config.reactions.isNotEmpty()) config.reactions
        else listOf(ReactionItem(
            text = config.customText.ifBlank { preset?.text ?: EventConfig.defaultText(config.eventType) },
            audioPath = config.customAudioPath.ifBlank { preset?.audioAssetPath ?: "" }
        ))

        return listOf(PriorityResolver.MatchedRule(
            ruleId = "base_${config.eventType.name}",
            priority = config.priority,
            reactions = reactions,
            notification = NotificationConfig(
                enabled = config.notificationEnabled,
                headsUp = config.showHeadsUp,
                playFeedbackAudio = config.soundEnabled,
                vibration = config.vibrationEnabled,
                lockScreen = config.lockScreenPublic
            ),
            minIntervalMinutes = config.minIntervalMinutes,
            displayName = EventConfig.displayName(config.eventType)
        ))
    }

    private fun buildBaseCondition(config: EventConfig): ConditionDef = when (config.eventType) {
        EventType.SHAKE -> ConditionDef(atomicType = AtomicEventType.SHAKE)
        EventType.DROP -> ConditionDef(atomicType = AtomicEventType.DROP)
        EventType.CHARGE_START -> ConditionDef(atomicType = AtomicEventType.CHARGE_START)
        EventType.LOW_BATTERY -> ConditionDef(
            atomicType = AtomicEventType.BATTERY_LEVEL,
            operator = ConditionDef.CompareOp.LESS_EQUAL, value = config.threshold
        )
        EventType.SCREEN_LONG_USAGE -> ConditionDef(
            atomicType = AtomicEventType.LONG_USAGE,
            operator = ConditionDef.CompareOp.GREATER_EQUAL, value = config.threshold
        )
    }

    private fun evaluateEventDefinitions(atomicType: AtomicEventType, now: Long): List<PriorityResolver.MatchedRule> {
        val defs = definitionRepository.loadAll()
            .filter { it.enabled && !it.isPreset && it.conditions.isNotEmpty() }

        return defs.mapNotNull { def ->
            // 所有条件 AND：全部满足才触发
            val satisfied = if (def.conditions.size == 1)
                evaluator.evaluate(def.conditions.first(), def.timeWindowMs, now)
            else
                evaluator.evaluateSequence(def.conditions, def.timeWindowMs, now)
            if (satisfied) PriorityResolver.MatchedRule(
                ruleId = def.id, priority = 5,
                reactions = def.reactions, notification = def.notification,
                minIntervalMinutes = def.minIntervalMinutes, displayName = def.name
            ) else null
        }
    }

    private fun mapAtomicToEventType(atomicType: AtomicEventType): EventType? = when (atomicType) {
        AtomicEventType.SHAKE -> EventType.SHAKE
        AtomicEventType.DROP -> EventType.DROP
        AtomicEventType.CHARGE_START -> EventType.CHARGE_START
        AtomicEventType.BATTERY_LEVEL -> EventType.LOW_BATTERY
        AtomicEventType.LONG_USAGE -> EventType.SCREEN_LONG_USAGE
        else -> null
    }

    companion object {
        private const val TAG = "RuleEngineV2"
        private const val BASE_EVENT_WINDOW_MS = 60_000L
    }
}
