package com.example.pat.engine

import com.example.pat.event.AtomicEvent
import com.example.pat.event.AtomicEventType
import com.example.pat.model.ConditionDef

/**
 * 当前设备状态提供者 —— 用于 [ConditionEvaluator] 查询实时设备状态。
 *
 * 当 [ConditionDef.checkCurrentState] = true 且事件类型是状态事件时，
 * 评估器通过此接口查询当前状态，而非查询事件历史缓冲区。
 */
interface DeviceStateProvider {
    /** 屏幕当前是否亮着 */
    val isScreenOn: Boolean
    /** 当前是否在充电 */
    val isCharging: Boolean
}

/**
 * 条件评估器 —— 对单个 [ConditionClause] 评估是否满足。
 *
 * 支持三种评估模式：
 * 1. **历史事件模式**（默认）：查询 [EventHistoryBuffer] 中过去窗口内的事件
 * 2. **当前状态模式**（checkCurrentState=true）：查询 [DeviceStateProvider] 获取实时状态
 * 3. **数值比较模式**：对携带数值的事件（BATTERY_LEVEL, LONG_USAGE）进行阈值比较
 *
 * @param history 事件历史缓冲区
 * @param stateProvider 当前设备状态提供者（可选，用于状态事件实时查询）
 */
class ConditionEvaluator(
    private val history: EventHistoryBuffer,
    private val stateProvider: DeviceStateProvider? = null
) {
    /**
     * 评估单个条件子句。
     *
     * @param clause 条件子句
     * @param windowMs 时间窗口（毫秒）
     * @param now 当前时间戳
     * @return 是否满足条件
     */
    fun evaluate(clause: ConditionDef, windowMs: Long, now: Long): Boolean {
        // ══════════════════════════════════════════════════════════════
        // 模式 1：当前状态查询（仅状态事件）
        // ══════════════════════════════════════════════════════════════
        if (clause.checkCurrentState && stateProvider != null) {
            return evaluateCurrentState(clause)
        }

        // ══════════════════════════════════════════════════════════════
        // 模式 2：历史事件查询
        // ══════════════════════════════════════════════════════════════

        // 1. 时间段条件：检查最近事件的小时是否在范围内
        if (clause.atomicType.supportsTimeRange && clause.valueMin != null && clause.valueMax != null) {
            val events = history.getInWindow(clause.atomicType, windowMs, now)
            if (events.isEmpty()) return false
            val last = events.last() as? AtomicEvent.LateNight ?: return false
            return isHourInRange(last.hour, clause.valueMin, clause.valueMax)
        }

        // 2. 检查发生次数
        if (clause.atomicType.supportsCount) {
            val actualCount = history.countInWindow(clause.atomicType, windowMs, now)
            if (actualCount < clause.count) return false
        } else {
            // 非计数事件：至少发生一次
            if (history.countInWindow(clause.atomicType, windowMs, now) < 1) return false
        }

        // 3. 如果有数值条件，检查最新一次事件的值
        if (clause.operator != null && clause.value != null) {
            val latestValue = getLatestValue(clause.atomicType, windowMs, now) ?: return false
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
     * 4. 所有条件按顺序满足 → true
     *
     * 注意：如果某个条件使用了 checkCurrentState，则跳过对该条件的历史查询，
     * 直接从 stateProvider 获取当前状态进行判断。
     */
    fun evaluateSequence(clauses: List<ConditionDef>, windowMs: Long, now: Long): Boolean {
        if (clauses.isEmpty()) return false
        if (clauses.size == 1) return evaluate(clauses[0], windowMs, now)

        val cutoff = now - windowMs
        var searchFrom = cutoff

        for (clause in clauses) {
            // 状态事件：直接检查当前状态
            if (clause.checkCurrentState && stateProvider != null) {
                if (!evaluateCurrentState(clause)) return false
                // 状态事件不更新 searchFrom（它是"当前"状态，没有时间戳）
                continue
            }

            val events = history.getInWindow(clause.atomicType, windowMs, now)
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
    // 当前状态评估
    // ══════════════════════════════════════════════════════════════

    /**
     * 通过 [DeviceStateProvider] 查询当前设备状态。
     *
     * 支持的状态事件：
     * - SCREEN_ON → stateProvider.isScreenOn
     * - SCREEN_OFF → !stateProvider.isScreenOn
     * - CHARGE_START → stateProvider.isCharging
     * - CHARGE_STOP → !stateProvider.isCharging
     */
    private fun evaluateCurrentState(clause: ConditionDef): Boolean {
        val provider = stateProvider ?: return false

        return when (clause.atomicType) {
            AtomicEventType.SCREEN_ON -> provider.isScreenOn
            AtomicEventType.SCREEN_OFF -> !provider.isScreenOn
            AtomicEventType.CHARGE_START -> provider.isCharging
            AtomicEventType.CHARGE_STOP -> !provider.isCharging
            // 其他类型不支持当前状态查询，回退到历史模式
            else -> {
                // 不应该走到这里，但作为安全回退
                false
            }
        }
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

    private fun compareValue(actual: Int, op: ConditionDef.CompareOp, expected: Int): Boolean {
        return when (op) {
            ConditionDef.CompareOp.LESS_THAN -> actual < expected
            ConditionDef.CompareOp.LESS_EQUAL -> actual <= expected
            ConditionDef.CompareOp.GREATER_THAN -> actual > expected
            ConditionDef.CompareOp.GREATER_EQUAL -> actual >= expected
            ConditionDef.CompareOp.EQUAL -> actual == expected
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
