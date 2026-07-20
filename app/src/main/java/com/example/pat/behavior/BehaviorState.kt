package com.example.pat.behavior

/**
 * 角色行为状态枚举。
 *
 * 表示虚拟角色当前所处的行为阶段，UI 层据此渲染对应界面。
 *
 * 参考文档：7.2 状态机设计
 */
enum class BehaviorState {
    /** 待机 —— 无事件触发时的默认状态 */
    IDLE,

    /** 开心 —— 积极事件（轻抚、喂食等）触发 */
    HAPPY,

    /** 受伤 —— 拍击/撞击事件触发 */
    HURT,

    /** 生气 —— 摇晃事件触发 */
    ANGRY,

    /** 害怕 —— 跌落事件触发 */
    SCARED,

    /** 睡眠 —— 长时间无操作 */
    SLEEPING
}

/**
 * 角色状态数据快照。
 *
 * 封装当前行为状态及最近一次事件的摘要信息，供 UI 层展示。
 *
 * @property state 当前行为状态
 * @property lastEventName 最近一次事件名称（如 "拍击"、"摇晃"）
 * @property lastEventDetail 最近一次事件的详细参数（如强度值）
 */
data class BehaviorSnapshot(
    val state: BehaviorState = BehaviorState.IDLE,
    val lastEventName: String = "None",
    val lastEventDetail: String = ""
)
