package com.example.pat.engine

import com.example.pat.event.AtomicEvent
import com.example.pat.event.AtomicEventType
import com.example.pat.model.ConditionClause

/**
 * 条件评估器 —— 对单个 [ConditionClause] 评估是否满足。
 *
 * 支持：
 * - 次数条件：某事件在时间窗口内发生 ≥N 次
 * - 数值条件：事件携带的数值满足比较操作（如电量 < 20%）
 *
 * @param history 事件历史缓冲区
 */
class ConditionEvaluator(
    private val history: EventHistoryBuffer
) {
    /**
     * 评估单个条件子句。
     *
     * @param clause 条件子句
     * @param windowMs 时间窗口（毫秒）
     * @param now 当前时间戳
     * @return 是否满足条件
     */
    fun evaluate(clause: ConditionClause, windowMs: Long, now: Long): Boolean {
        // 1. 时间段条件：检查最近事件的小时是否在范围内
        if (clause.eventType.supportsTimeRange && clause.valueMin != null && clause.valueMax != null) {
            val events = history.getInWindow(clause.eventType, windowMs, now)
            if (events.isEmpty()) return false
            val last = events.last() as? AtomicEvent.LateNight ?: return false
            return isHourInRange(last.hour, clause.valueMin, clause.valueMax)
        }

        // 2. 检查发生次数
        if (clause.eventType.supportsCount) {
            val actualCount = history.countInWindow(clause.eventType, windowMs, now)
            if (actualCount < clause.count) return false
        } else {
            // 非计数事件：至少发生一次
            if (history.countInWindow(clause.eventType, windowMs, now) < 1) return false
        }

        // 3. 如果有数值条件，检查最新一次事件的值
        if (clause.operator != null && clause.value != null) {
            val latestValue = getLatestValue(clause.eventType, windowMs, now) ?: return false
            return compareValue(latestValue, clause.operator, clause.value)
        }

        return true
    }

    /**
     * 评估序列条件：条件列表中的事件必须按顺序发生。
     *
     * 算法：
     * 1. 找到条件[0]在窗口内的事件时间 t0
     * 2. 找到条件[1]在 t0 之后、窗口内的事件时间 t1
     * 3. 递推...
     * 4. 如果所有条件都能按顺序找到 → true
     */
    fun evaluateSequence(clauses: List<ConditionClause>, windowMs: Long, now: Long): Boolean {
        if (clauses.isEmpty()) return false
        if (clauses.size == 1) return evaluate(clauses[0], windowMs, now)

        val cutoff = now - windowMs
        var searchFrom = cutoff

        for (clause in clauses) {
            val events = history.getInWindow(clause.eventType, windowMs, now)
                .filter { it.timestamp >= searchFrom }

            // 检查是否有足够次数满足当前条件
            if (events.size < clause.count) return false

            // 对于数值条件，检查最后一个事件的值
            if (clause.operator != null && clause.value != null) {
                val lastEvent = events.last()
                val value = extractValue(lastEvent) ?: return false
                if (!compareValue(value, clause.operator, clause.value)) return false
            }

            // 更新搜索起点：从此条件最后一个事件的时间之后开始
            searchFrom = events.last().timestamp + 1
        }

        return true
    }

    // ══════════════════════════════════════════════════════════════
    // 内部
    // ══════════════════════════════════════════════════════════════

    private fun getLatestValue(type: AtomicEventType, windowMs: Long, now: Long): Int? {
        val events = history.getInWindow(type, windowMs, now)
        val last = events.lastOrNull() ?: return null
        return extractValue(last)
    }

    private fun extractValue(event: AtomicEvent): Int? = when (event) {
        is AtomicEvent.BatteryLevel -> event.percent
        is AtomicEvent.LongUsage -> event.minutes
        else -> null
    }

    private fun compareValue(actual: Int, op: ConditionClause.CompareOp, expected: Int): Boolean {
        return when (op) {
            ConditionClause.CompareOp.LESS_THAN -> actual < expected
            ConditionClause.CompareOp.LESS_EQUAL -> actual <= expected
            ConditionClause.CompareOp.GREATER_THAN -> actual > expected
            ConditionClause.CompareOp.GREATER_EQUAL -> actual >= expected
            ConditionClause.CompareOp.EQUAL -> actual == expected
        }
    }

    /**
     * 判断小时是否在指定范围内（支持跨天，如 22:00-06:00）。
     */
    private fun isHourInRange(hour: Int, start: Int, end: Int): Boolean {
        return if (start <= end) {
            hour in start..end
        } else {
            // 跨天范围，如 22-6
            hour >= start || hour <= end
        }
    }
}
