package com.example.pat.engine

import android.util.Log
import com.example.pat.data.UserRuleRepository
import com.example.pat.event.AtomicEvent
import com.example.pat.event.AtomicEventBus
import com.example.pat.event.toType
import com.example.pat.model.ConflictStrategy
import com.example.pat.model.ConditionOperator
import com.example.pat.model.UserRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 规则引擎 v2 —— 有状态、支持组合条件的规则匹配。
 *
 * 工作流程：
 * ```
 * AtomicEventBus → collect → EventHistoryBuffer.record()
 *   → prune expired events
 *   → find relevant UserRules (by event type)
 *   → evaluate each rule's conditions against the time window
 *   → PriorityResolver: pick winner(s)
 *   → callback onRuleMatched(UserRule)
 * ```
 *
 * 与当前 [com.example.pat.event.EventDispatcher] + RuleEngine 并行运行，
 * 不替换现有系统。两者各自从 EventBus/AtomicEventBus 收集事件。
 *
 * @param ruleRepository 用户规则仓库
 * @param history 事件历史缓冲区
 * @param scope 协程作用域
 * @param onRuleMatched 规则匹配回调（由 Service 层接入 ResponseManager）
 */
class RuleEngineV2(
    private val ruleRepository: UserRuleRepository,
    private val history: EventHistoryBuffer = EventHistoryBuffer(),
    private val scope: CoroutineScope
) {
    private val evaluator = ConditionEvaluator(history)

    /** 每条规则的冷却追踪：ruleId → 上次触发时间 */
    private val cooldowns = mutableMapOf<String, Long>()

    /** 规则匹配回调 */
    var onRuleMatched: ((UserRule) -> Unit)? = null

    private var collectionJob: Job? = null

    /** 匹配到的最新规则列表（供 UI 查询） */
    @Volatile
    var lastMatchedRules: List<UserRule> = emptyList()
        private set

    /**
     * 开始从 [AtomicEventBus] 收集事件并评估规则。
     */
    fun start() {
        collectionJob?.cancel()
        collectionJob = scope.launch {
            AtomicEventBus.events.collectLatest { event ->
                processEvent(event)
            }
        }
        Log.i(TAG, "RuleEngineV2 started")
    }

    /**
     * 停止事件收集。
     */
    fun stop() {
        collectionJob?.cancel()
        collectionJob = null
        Log.i(TAG, "RuleEngineV2 stopped")
    }

    /**
     * 重新加载规则（当用户增删改规则后调用）。
     */
    fun reloadRules() {
        // 规则通过 ruleRepository 实时查询，无需缓存
        Log.d(TAG, "Rules will be reloaded on next event")
    }

    // ══════════════════════════════════════════════════════════════
    // 内部
    // ══════════════════════════════════════════════════════════════

    private fun processEvent(event: AtomicEvent) {
        val now = System.currentTimeMillis()

        // 1. 记录 + 清理
        history.record(event)
        history.prune(now)

        // 2. 找到相关规则
        val eventType = event.toType()
        val relevantRules = ruleRepository.findByEventType(eventType)
            .filter { it.enabled && it.conditions.isNotEmpty() }

        if (relevantRules.isEmpty()) return

        // 3. 评估每条规则
        val matched = mutableListOf<UserRule>()
        for (rule in relevantRules) {
            // 冷却检查
            val lastFire = cooldowns[rule.id] ?: 0L
            val cooldownMs = rule.minIntervalMinutes * 60_000L
            if (cooldownMs > 0 && now - lastFire < cooldownMs) continue

            // 条件评估
            val satisfied = when (rule.operator) {
                ConditionOperator.AND ->
                    rule.conditions.all { evaluator.evaluate(it, rule.timeWindowMs, now) }
                ConditionOperator.OR ->
                    rule.conditions.any { evaluator.evaluate(it, rule.timeWindowMs, now) }
                ConditionOperator.SEQUENCE ->
                    evaluator.evaluateSequence(rule.conditions, rule.timeWindowMs, now)
            }

            if (satisfied) {
                matched.add(rule)
            }
        }

        if (matched.isEmpty()) return

        // 4. 优先级解决
        val toExecute = resolveConflicts(matched, now)

        // 5. 执行
        for (rule in toExecute) {
            cooldowns[rule.id] = now
            Log.i(TAG, "Rule matched: \"${rule.name}\" (priority=${rule.priority}, conditions=${rule.conditionSummary})")
            onRuleMatched?.invoke(rule)
        }

        lastMatchedRules = toExecute
    }

    /**
     * 优先级解决 + 冲突处理。
     */
    private fun resolveConflicts(matched: List<UserRule>, now: Long): List<UserRule> {
        if (matched.size == 1) return matched

        // 采用第一条规则的冲突策略
        val strategy = matched.first().conflictStrategy

        val sorted = matched.sortedByDescending { it.priority }

        return when (strategy) {
            ConflictStrategy.HIGHEST_PRIORITY -> {
                val top = sorted.first()
                Log.d(TAG, "Conflict resolved (HIGHEST_PRIORITY): selected \"${top.name}\"")
                listOf(top)
            }
            ConflictStrategy.EXECUTE_ALL -> {
                Log.d(TAG, "Conflict resolved (EXECUTE_ALL): ${sorted.size} rules")
                sorted
            }
            ConflictStrategy.SEQUENTIAL -> {
                Log.d(TAG, "Conflict resolved (SEQUENTIAL): ${sorted.size} rules in order")
                sorted
            }
        }
    }

    companion object {
        private const val TAG = "RuleEngineV2"
    }
}
