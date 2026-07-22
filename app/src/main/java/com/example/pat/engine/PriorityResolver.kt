package com.example.pat.engine

import android.util.Log
import com.example.pat.model.NotificationConfig
import com.example.pat.model.ReactionItem

/**
 * 全局优先级解决器 —— 从所有匹配的规则中选出最终执行列表。
 *
 * 统一处理基础事件（EventConfig）和自定义规则（UserRule）的优先级竞争。
 *
 * 算法：
 * 1. 按 priority 降序排列
 * 2. 过滤掉冷却期内的规则
 * 3. 取最高优先级组
 * 4. 如果组内只有一个 → 直接返回
 * 5. 如果组内有多个 → 按 [ConflictStrategy] 决定
 *    - HIGHEST_PRIORITY: 只取第一个（优先自定义规则）
 *    - EXECUTE_ALL: 全部执行
 *    - SEQUENTIAL: 按顺序依次执行
 */
class PriorityResolver {

    /**
     * 统一匹配结果 —— 引擎内部使用。
     */
    data class MatchedRule(
        val ruleId: String,
        val priority: Int = 5,
        val reactions: List<ReactionItem>,
        val notification: NotificationConfig = NotificationConfig(),
        val minIntervalMinutes: Int = 120,
        val displayName: String = ""
    )

    private val cooldowns = CooldownManager()

    /**
     * 解决优先级冲突。
     *
     * @param matched 所有匹配的规则列表（可能来自基础事件和自定义规则）
     * @param now 当前时间戳（用于冷却检查）
     * @return 应执行的规则列表
     */
    fun resolve(matched: List<MatchedRule>, now: Long): List<MatchedRule> {
        if (matched.isEmpty()) return emptyList()
        if (matched.size == 1) {
            val single = matched.first()
            return if (cooldowns.canExecute(single.ruleId, single.minIntervalMinutes, now)) {
                listOf(single)
            } else {
                Log.d(TAG, "Single matched rule \"${single.displayName}\" on cooldown — skipping")
                emptyList()
            }
        }

        // 1. 按优先级降序
        val sorted = matched.sortedByDescending { it.priority }
        val topPriority = sorted.first().priority
        val topRules = sorted.takeWhile { it.priority == topPriority }

        Log.d(TAG, "Resolving ${matched.size} matched rules, top priority=$topPriority, top count=${topRules.size}")

        // 2. 过滤冷却
        val eligible = topRules.filter {
            cooldowns.canExecute(it.ruleId, it.minIntervalMinutes, now)
        }
        if (eligible.isEmpty()) {
            Log.d(TAG, "All top-priority rules on cooldown")
            return emptyList()
        }

        // 3. 取最高优先级中第一个（简单优先级策略）
        val selected = eligible.first()
        Log.i(TAG, "Selected: \"${selected.displayName}\" (priority=${selected.priority})")
        return listOf(selected)
    }

    /**
     * 标记规则已执行（更新冷却时间）。
     */
    fun markExecuted(ruleId: String, now: Long) {
        cooldowns.markExecuted(ruleId, now)
    }

    /**
     * 获取规则剩余冷却秒数（用于 UI 展示）。
     */
    fun remainingCooldownSec(ruleId: String, minIntervalMinutes: Int, now: Long): Long {
        return cooldowns.remainingSec(ruleId, minIntervalMinutes, now)
    }

    companion object {
        private const val TAG = "PriorityResolver"
    }
}
