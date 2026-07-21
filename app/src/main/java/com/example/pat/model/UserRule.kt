package com.example.pat.model

/**
 * 条件组合操作符。
 */
enum class ConditionOperator {
    /** 全部条件满足（在同一时间窗口内） */
    AND,
    /** 任意一个条件满足 */
    OR,
    /** 按顺序依次满足（A → B → C，在同一时间窗口内） */
    SEQUENCE
}

/**
 * 冲突解决策略。
 */
enum class ConflictStrategy {
    /** 全部执行（多个规则同时触发都执行） */
    EXECUTE_ALL,
    /** 只执行最高优先级（默认） */
    HIGHEST_PRIORITY,
    /** 间隔执行（按优先级排序后间隔 5 秒依次执行） */
    SEQUENTIAL
}

/**
 * 用户自定义事件规则。
 *
 * 一条规则 = 名称 + 条件列表 + 操作符 + 时间窗口 + 优先级 + 反馈引用。
 *
 * 与 [EventConfig] 的关系：
 * - EventConfig 是系统预设的 1:1 映射（一个事件类型 → 一条规则）
 * - UserRule 是用户自定义的 N:M 组合（多个条件 → 一条规则）
 * - 两者在 RuleEngineV2 中统一处理
 */
data class UserRule(
    val id: String = "",
    /** 用户命名的规则名称（如"睡觉提醒"） */
    val name: String,
    /** 条件列表（至少 1 个） */
    val conditions: List<ConditionClause>,
    /** 条件组合方式 */
    val operator: ConditionOperator = ConditionOperator.AND,
    /** 所有条件必须在多长时间内满足（毫秒） */
    val timeWindowMs: Long = 10_000L,
    /** 优先级（1-10，数字越大优先级越高） */
    val priority: Int = 5,
    /** 是否启用 */
    val enabled: Boolean = true,
    /** 反馈文本（用户自定义） */
    val reactionText: String = "",
    /** 反馈音频路径（内置预设路径 或 用户上传文件路径） */
    val reactionAudioPath: String = "",
    // ── 通知偏好（同 EventConfig） ──
    val notificationEnabled: Boolean = true,
    val vibrationEnabled: Boolean = false,
    val soundEnabled: Boolean = false,
    val showHeadsUp: Boolean = true,
    val lockScreenPublic: Boolean = true,
    /** 最小触发间隔（分钟），0 = 无限制 */
    val minIntervalMinutes: Int = 10,
    /** 冲突解决策略 */
    val conflictStrategy: ConflictStrategy = ConflictStrategy.HIGHEST_PRIORITY
) {
    /** 条件的可读摘要 */
    val conditionSummary: String
        get() = when {
            conditions.isEmpty() -> "无条件"
            conditions.size == 1 -> conditions[0].displayText
            else -> conditions.joinToString(" ${operator.name} ") { it.displayText }
        }
}
