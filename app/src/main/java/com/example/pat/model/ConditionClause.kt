package com.example.pat.model

import com.example.pat.event.AtomicEventType

/**
 * 规则中的单个条件子句。
 *
 * 示例：
 * - SHAKE 发生 3 次：    ConditionClause(SHAKE, count=3)
 * - 电量 < 20%：         ConditionClause(BATTERY_LEVEL, CompareOp.LESS_THAN, value=20)
 * - 22:00-06:00 使用：   ConditionClause(LATE_NIGHT, valueMin=22, valueMax=6)
 *
 * @property eventType 原子事件类型
 * @property operator 比较操作符（仅 hasValue 事件使用）
 * @property value 比较阈值（仅 hasValue 事件使用）
 * @property count 需要发生的次数（仅 supportsCount 事件使用）
 * @property valueMin 时间段起始小时 0-23（仅 supportsTimeRange 事件使用）
 * @property valueMax 时间段结束小时 0-23（仅 supportsTimeRange 事件使用）
 * @property checkCurrentState 是否查询当前设备状态而非事件历史。仅对状态事件有效
 *                             （SCREEN_ON, SCREEN_OFF, CHARGE_START, CHARGE_STOP）。
 *                             例如 SCREEN_ON + checkCurrentState=true 表示"屏幕当前是亮的"
 *                             而非"在过去窗口内发生过亮屏事件"
 */
data class ConditionClause(
    val eventType: AtomicEventType,
    val operator: CompareOp? = null,
    val value: Int? = null,
    val count: Int = 1,
    val valueMin: Int? = null,
    val valueMax: Int? = null,
    /** 仅状态事件有效：是否查询当前设备状态而非事件历史 */
    val checkCurrentState: Boolean = false
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
        get() = when {
            eventType.supportsTimeRange && valueMin != null && valueMax != null ->
                "${eventType.displayName} ${valueMin}:00-${valueMax}:00"
            eventType.hasValue && operator != null && value != null ->
                "${eventType.displayName} ${operator.symbol} $value"
            eventType.supportsCount && count > 1 ->
                "${eventType.displayName} ${count}次"
            else -> eventType.displayName
        }
}
