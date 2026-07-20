package com.example.pat.event

/**
 * 动作事件类型枚举。
 *
 * 用于在不需要携带额外参数时进行简单的事件类型判断。
 * 是 [MotionEvent] 密封类的轻量级分类器。
 *
 * 每个枚举值对应 [MotionEvent] 中的一个具体子类。
 *
 * 参考文档：6.2 事件类型定义
 */
enum class MotionType {
    /** 无事件 / 待机 */
    NONE,

    /** 摇晃 */
    SHAKE,

    /** 拍击/撞击 */
    IMPACT,

    /** 跌落 */
    DROP,

    /** 倾斜 */
    TILT
}

/**
 * 动作事件密封类。
 *
 * 整个系统的核心事件类型。detector 层识别出物理动作后，映射为 [MotionEvent] 子类
 * 发送到 [EventBus]，behavior 层根据事件类型驱动反馈。
 *
 * 每个子类通过 [type] 属性关联到对应的 [MotionType] 枚举值，
 * 方便在无需携带参数时进行 switch/match 判断。
 *
 * 设计原则：
 * - 事件本身不携带业务逻辑，仅为语义标记 + 必要参数
 * - 新增动作类型只需添加新的子类，不影响现有代码
 *
 * 参考文档：6.2 事件定义
 */
sealed class MotionEvent {

    /** 对应的事件类型枚举值，用于简单分类判断。 */
    open val type: MotionType get() = MotionType.NONE

    /**
     * 撞击/拍击事件。
     * @param intensity 归一化强度 [0f, 1f]
     */
    data class Impact(val intensity: Float) : MotionEvent() {
        override val type: MotionType get() = MotionType.IMPACT
    }

    /** 摇晃事件。 */
    data object Shake : MotionEvent() {
        override val type: MotionType get() = MotionType.SHAKE
    }

    /**
     * 跌落事件。
     * @param impactForce 撞击原始峰值 (m/s²)
     */
    data class Drop(val impactForce: Float) : MotionEvent() {
        override val type: MotionType get() = MotionType.DROP
    }

    /**
     * 倾斜事件（预留）。
     * @param pitch 俯仰角 [-90, 90]
     * @param roll 横滚角 [-90, 90]
     */
    data class Tilt(val pitch: Float, val roll: Float) : MotionEvent() {
        override val type: MotionType get() = MotionType.TILT
    }

    /** 空闲事件：超过设定时长未检测到任何动作。 */
    data object Idle : MotionEvent()
}
