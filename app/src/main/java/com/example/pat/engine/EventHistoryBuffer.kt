package com.example.pat.engine

import com.example.pat.event.AtomicEvent
import com.example.pat.event.AtomicEventType
import com.example.pat.event.toType

/**
 * 事件历史缓冲区 —— 滑动时间窗口。
 *
 * 保存最近 [maxWindowMs] 内所有原子事件，按类型分组索引。
 * 为 [ConditionEvaluator] 提供高效的时间窗口查询。
 *
 * 内存占用估算：
 * - 保留 2 小时窗口
 * - 传感器事件（SHAKE/IMPACT）约 0-5 次/秒（含检测器内部冷却）
 * - 系统事件（SCREEN/BATTERY）约 0-2 次/秒
 * - 总计约 5 × 7200 = 36,000 条
 *
 * 线程安全：所有公共方法同步于内部锁。
 *
 * @param maxWindowMs 最大保留窗口（毫秒），默认 2 小时
 */
class EventHistoryBuffer(
    private val maxWindowMs: Long = 2 * 60 * 60 * 1000L
) {
    /** 按类型分组的事件队列（旧 → 新顺序） */
    private val eventsByType = mutableMapOf<AtomicEventType, ArrayDeque<AtomicEvent>>()

    private val lock = Any()

    /**
     * 记录一个原子事件。
     */
    fun record(event: AtomicEvent) {
        val type = event.toType()
        synchronized(lock) {
            val deque = eventsByType.getOrPut(type) { ArrayDeque() }
            deque.addLast(event)
        }
    }

    /**
     * 查询某类型在最近 [windowMs] 内的事件数量。
     */
    fun countInWindow(type: AtomicEventType, windowMs: Long, now: Long = System.currentTimeMillis()): Int {
        val cutoff = now - windowMs
        synchronized(lock) {
            val deque = eventsByType[type] ?: return 0
            // 从前往后找第一个在窗口内的
            var count = 0
            for (i in deque.lastIndex downTo 0) {
                if (deque[i].timestamp >= cutoff) count++ else break
            }
            return count
        }
    }

    /**
     * 查询某类型在最近 [windowMs] 内的所有事件（旧 → 新）。
     */
    fun getInWindow(type: AtomicEventType, windowMs: Long, now: Long = System.currentTimeMillis()): List<AtomicEvent> {
        val cutoff = now - windowMs
        synchronized(lock) {
            val deque = eventsByType[type] ?: return emptyList()
            return deque.filter { it.timestamp >= cutoff }
        }
    }

    /**
     * 获取某类型最近一次发生的时间戳。
     */
    fun lastOccurrence(type: AtomicEventType): Long? {
        synchronized(lock) {
            return eventsByType[type]?.lastOrNull()?.timestamp
        }
    }

    /**
     * 清理所有超过 [maxWindowMs] 的事件。
     * 建议在每次 record 后调用。
     */
    fun prune(now: Long = System.currentTimeMillis()) {
        val cutoff = now - maxWindowMs
        synchronized(lock) {
            for ((_, deque) in eventsByType) {
                while (deque.isNotEmpty() && deque.first().timestamp < cutoff) {
                    deque.removeFirst()
                }
            }
            // 移除空队列
            eventsByType.entries.removeAll { it.value.isEmpty() }
        }
    }

    /**
     * 获取当前缓冲区中所有事件的总数（用于监控）。
     */
    fun size(): Int {
        synchronized(lock) {
            return eventsByType.values.sumOf { it.size }
        }
    }

    /**
     * 清空所有历史。
     */
    fun clear() {
        synchronized(lock) {
            eventsByType.clear()
        }
    }
}
