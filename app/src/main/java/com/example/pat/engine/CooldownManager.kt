package com.example.pat.engine

import android.util.Log

/**
 * 统一冷却管理器 —— 所有规则（基础事件 + 自定义规则）共享的冷却追踪器。
 *
 * 替代旧版分散在 [EventDispatcher] 和 [RuleEngineV2] 中的独立冷却逻辑。
 *
 * 线程安全：所有公共方法同步于内部锁。
 *
 * 使用方式：
 * ```
 * val cooldown = CooldownManager()
 * if (cooldown.canExecute("rule_1", 10, now)) {
 *     executeRule()
 *     cooldown.markExecuted("rule_1", now)
 * }
 * ```
 */
class CooldownManager {

    /** ruleId → 上次触发时间戳 */
    private val lastFireTimes = mutableMapOf<String, Long>()
    private val lock = Any()

    /**
     * 检查规则是否可执行（冷却已过）。
     *
     * @param ruleId 规则唯一标识
     * @param minIntervalMinutes 最小触发间隔（分钟），0 = 无冷却
     * @param now 当前时间戳
     * @return true 如果冷却已过或冷却为 0
     */
    fun canExecute(ruleId: String, minIntervalMinutes: Int, now: Long): Boolean {
        if (minIntervalMinutes <= 0) return true
        synchronized(lock) {
            val lastFire = lastFireTimes[ruleId] ?: return true
            val cooldownMs = minIntervalMinutes * 60_000L
            val elapsed = now - lastFire
            return elapsed >= cooldownMs
        }
    }

    /**
     * 标记规则刚刚执行过。
     */
    fun markExecuted(ruleId: String, now: Long) {
        synchronized(lock) {
            lastFireTimes[ruleId] = now
        }
        Log.d(TAG, "Cooldown updated: ruleId=$ruleId")
    }

    /**
     * 获取规则剩余冷却时间（秒）。
     *
     * @return 剩余秒数，0 表示冷却已过
     */
    fun remainingSec(ruleId: String, minIntervalMinutes: Int, now: Long): Long {
        if (minIntervalMinutes <= 0) return 0
        synchronized(lock) {
            val lastFire = lastFireTimes[ruleId] ?: return 0
            val cooldownMs = minIntervalMinutes * 60_000L
            val elapsed = now - lastFire
            if (elapsed >= cooldownMs) return 0
            return (cooldownMs - elapsed) / 1000
        }
    }

    /**
     * 清除所有冷却记录（用于重置）。
     */
    fun clear() {
        synchronized(lock) {
            lastFireTimes.clear()
        }
    }

    companion object {
        private const val TAG = "CooldownManager"
    }
}
