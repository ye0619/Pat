package com.example.pat.model

import com.example.pat.event.AtomicEventType

/**
 * 规则中的单个条件子句。
 *
 * 示例：
 * - SHAKE 发生 1 次：  ConditionClause(SHAKE)
 * - 电量 < 20%：       ConditionClause(BATTERY_LEVEL, CompareOp.LESS_THAN, 20)
 * - 撞击发生 4 次：    ConditionClause(IMPACT, count = 4)
 *
 * @property eventType 原子事件类型
 * @property operator 比较操作符（仅 hasValue=true 的事件使用）
 * @property value 比较阈值（仅 hasValue=true 的事件使用）
 * @property count 需要发生的次数（默认 1）
 */
data class ConditionClause(
    val eventType: AtomicEventType,
    val operator: CompareOp? = null,
    val value: Int? = null,
    val count: Int = 1
) {
    enum class CompareOp(val symbol: String) {
        LESS_THAN("<"),
        LESS_EQUAL("<="),
        GREATER_THAN(">"),
        GREATER_EQUAL(">="),
        EQUAL("=")
    }

    /** 条件的可读描述 */
    val displayText: String
        get() {
            val base = eventType.displayName
            val countStr = if (count > 1) " ${count}次" else ""
            return if (operator != null && value != null) {
                "$base ${operator.symbol} $value$countStr"
            } else {
                "$base$countStr"
            }
        }
}
