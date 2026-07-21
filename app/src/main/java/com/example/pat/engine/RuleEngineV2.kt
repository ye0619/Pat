package com.example.pat.engine

import android.util.Log
import com.example.pat.data.EventConfigRepository
import com.example.pat.data.UserRuleRepository
import com.example.pat.event.AtomicEvent
import com.example.pat.event.AtomicEventBus
import com.example.pat.event.AtomicEventType
import com.example.pat.event.EventType
import com.example.pat.event.toType
import com.example.pat.model.ConditionClause
import com.example.pat.model.ConditionOperator
import com.example.pat.model.ConflictStrategy
import com.example.pat.model.EventConfig
import com.example.pat.model.ReactionItem
import com.example.pat.model.UserRule
import com.example.pat.model.toNotificationPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 统一规则引擎 —— 同时处理基础事件（EventConfig）和自定义规则（UserRule）。
 *
 * 核心改进：
 * - 废除旧双管道架构，统一从 [AtomicEventBus] 收集事件
 * - 基础事件自动转为隐式规则（单条件 AND），与自定义规则在同一优先级池中竞争
 * - 先收集所有匹配规则 → [PriorityResolver] 统一排序 → 最高优先级执行
 * - 通过 [DeviceStateProvider] 支持状态事件实时查询
 */
class RuleEngineV2(
    private val configRepository: EventConfigRepository,
    private val ruleRepository: UserRuleRepository,
    private val history: EventHistoryBuffer = EventHistoryBuffer(),
    private val scope: CoroutineScope,
    private val stateProvider: DeviceStateProvider? = null
) {
    private val evaluator = ConditionEvaluator(history, stateProvider)
    private val priorityResolver = PriorityResolver()

    /** 规则匹配回调 — 传入统一 MatchedRule（来自基础事件或自定义规则） */
    var onRuleMatched: ((PriorityResolver.MatchedRule) -> Unit)? = null

    private var collectionJob: Job? = null

    /** 匹配到的最新规则列表（供 UI 查询） */
    @Volatile
    var lastMatchedRules: List<PriorityResolver.MatchedRule> = emptyList()
        private set

    /** 今日触发次数 */
    @Volatile
    var todayTriggerCount: Int = 0
        private set

    /** 最近触发记录（供 UI 展示） */
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
            AtomicEventBus.events.collectLatest { event ->
                processEvent(event)
            }
        }
        Log.i(TAG, "Unified RuleEngineV2 started")
    }

    fun stop() {
        collectionJob?.cancel()
        collectionJob = null
        Log.i(TAG, "RuleEngineV2 stopped")
    }

    fun reloadRules() {
        Log.d(TAG, "Rules will be reloaded on next event")
    }

    // ══════════════════════════════════════════════════════════════
    // 内部：事件处理
    // ══════════════════════════════════════════════════════════════

    private fun processEvent(event: AtomicEvent) {
        val now = System.currentTimeMillis()

        // 1. 记录 + 清理
        history.record(event)
        history.prune(now)

        // 2. 评估基础事件（来自 EventConfig）
        val eventType = event.toType()
        val matchedBase = evaluateBaseEvent(eventType, now)

        // 3. 评估自定义规则（来自 UserRuleRepository）
        val matchedCustom = evaluateCustomRules(eventType, now)

        // 4. 合并
        val allMatched = matchedBase + matchedCustom
        if (allMatched.isEmpty()) return

        // 5. 全局优先级解决（含冷却检查）
        val toExecute = priorityResolver.resolve(allMatched, now)

        // 6. 执行
        for (rule in toExecute) {
            priorityResolver.markExecuted(rule.ruleId, now)
            Log.i(TAG, "Rule matched: \"${rule.displayName}\" (priority=${rule.priority})")

            // 记录触发
            val reaction = rule.reactions.firstOrNull() ?: ReactionItem()
            todayTriggerCount++
            _recentTriggers.add(0, RecentTrigger(
                displayName = rule.displayName,
                displayText = reaction.text.ifBlank { "\"${rule.displayName}\" 已触发" },
                timestamp = now
            ))
            if (_recentTriggers.size > 20) {
                _recentTriggers.removeAt(_recentTriggers.lastIndex)
            }

            onRuleMatched?.invoke(rule)
        }

        lastMatchedRules = toExecute
    }

    // ══════════════════════════════════════════════════════════════
    // 基础事件评估
    // ══════════════════════════════════════════════════════════════

    private fun evaluateBaseEvent(atomicType: AtomicEventType, now: Long): List<PriorityResolver.MatchedRule> {
        val baseType = mapAtomicToEventType(atomicType) ?: return emptyList()
        val config = configRepository.getByEventType(baseType) ?: return emptyList()
        if (!config.enabled) return emptyList()

        // 构建条件并评估
        val condition = buildBaseCondition(config)
        val satisfied = evaluator.evaluate(condition, BASE_EVENT_WINDOW_MS, now)
        if (!satisfied) return emptyList()

        val reactions = if (config.reactions.isNotEmpty()) {
            config.reactions
        } else {
            listOf(ReactionItem(text = config.customText, audioPath = config.customAudioPath))
        }

        return listOf(PriorityResolver.MatchedRule(
            ruleId = "base_${config.eventType.name}",
            priority = config.priority,
            reactions = reactions,
            notification = config.toNotificationPreference(),
            minIntervalMinutes = config.minIntervalMinutes,
            conflictStrategy = ConflictStrategy.HIGHEST_PRIORITY,
            displayName = EventConfig.displayName(config.eventType)
        ))
    }

    private fun buildBaseCondition(config: EventConfig): ConditionClause {
        return when (config.eventType) {
            EventType.SHAKE -> ConditionClause(eventType = AtomicEventType.SHAKE)
            EventType.IMPACT -> ConditionClause(eventType = AtomicEventType.IMPACT)
            EventType.CHARGE_START -> ConditionClause(eventType = AtomicEventType.CHARGE_START)
            EventType.LOW_BATTERY -> ConditionClause(
                eventType = AtomicEventType.BATTERY_LEVEL,
                operator = ConditionClause.CompareOp.LESS_EQUAL,
                value = config.threshold
            )
            EventType.SCREEN_LONG_USAGE -> ConditionClause(
                eventType = AtomicEventType.LONG_USAGE,
                operator = ConditionClause.CompareOp.GREATER_EQUAL,
                value = config.threshold
            )
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 自定义规则评估
    // ══════════════════════════════════════════════════════════════

    private fun evaluateCustomRules(atomicType: AtomicEventType, now: Long): List<PriorityResolver.MatchedRule> {
        val relevantRules = ruleRepository.findByEventType(atomicType)
            .filter { it.enabled && it.conditions.isNotEmpty() }

        val matched = mutableListOf<PriorityResolver.MatchedRule>()
        for (rule in relevantRules) {
            val satisfied = when (rule.operator) {
                ConditionOperator.AND ->
                    rule.conditions.all { evaluator.evaluate(it, rule.timeWindowMs, now) }
                ConditionOperator.OR ->
                    rule.conditions.any { evaluator.evaluate(it, rule.timeWindowMs, now) }
                ConditionOperator.SEQUENCE ->
                    evaluator.evaluateSequence(rule.conditions, rule.timeWindowMs, now)
            }

            if (satisfied) {
                val reactions = if (rule.reactions.isNotEmpty()) {
                    rule.reactions
                } else {
                    listOf(ReactionItem(text = rule.reactionText, audioPath = rule.reactionAudioPath))
                }

                matched.add(PriorityResolver.MatchedRule(
                    ruleId = rule.id,
                    priority = rule.priority,
                    reactions = reactions,
                    notification = rule.toNotificationPreference(),
                    minIntervalMinutes = rule.minIntervalMinutes,
                    conflictStrategy = rule.conflictStrategy,
                    displayName = rule.name
                ))
            }
        }

        return matched
    }

    // ══════════════════════════════════════════════════════════════
    // 类型映射
    // ══════════════════════════════════════════════════════════════

    private fun mapAtomicToEventType(atomicType: AtomicEventType): EventType? {
        return when (atomicType) {
            AtomicEventType.SHAKE -> EventType.SHAKE
            AtomicEventType.IMPACT -> EventType.IMPACT
            AtomicEventType.CHARGE_START -> EventType.CHARGE_START
            AtomicEventType.BATTERY_LEVEL -> EventType.LOW_BATTERY
            AtomicEventType.LONG_USAGE -> EventType.SCREEN_LONG_USAGE
            else -> null
        }
    }

    companion object {
        private const val TAG = "RuleEngineV2"
        /** 基础事件的时间窗口（60 秒 — 足够覆盖最新的 BatteryLevel/LongUsage 事件） */
        private const val BASE_EVENT_WINDOW_MS = 60_000L
    }
}
