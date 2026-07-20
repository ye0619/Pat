package com.example.pat.event

/**
 * 动作事件密封类。
 *
 * 整个系统的核心事件类型。detector 层识别出物理动作后，映射为 [MotionEvent] 子类
 * 发送到 [EventBus]，behavior 层根据事件类型驱动反馈。
 *
 * 设计原则：
 * - 事件本身不携带业务逻辑，仅为语义标记 + 必要参数
 * - 新增动作类型只需添加新的子类，不影响现有代码
 *
 * 参考文档：6.2 事件定义
 */
sealed class MotionEvent {

    /**
     * 撞击/拍击事件。
     * @param intensity 归一化强度 [0f, 1f]
     */
    data class Impact(val intensity: Float) : MotionEvent()

    /** 摇晃事件。 */
    data object Shake : MotionEvent()

    /**
     * 跌落事件。
     * @param impactForce 撞击原始峰值 (m/s²)
     */
    data class Drop(val impactForce: Float) : MotionEvent()

    /**
     * 倾斜事件（预留）。
     * @param pitch 俯仰角 [-90, 90]
     * @param roll 横滚角 [-90, 90]
     */
    data class Tilt(val pitch: Float, val roll: Float) : MotionEvent()

    /** 空闲事件：超过设定时长未检测到任何动作。 */
    data object Idle : MotionEvent()
}
