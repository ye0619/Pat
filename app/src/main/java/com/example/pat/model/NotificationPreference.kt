package com.example.pat.model

/**
 * 通知偏好 —— 从 EventConfig / UserRule 中提取的公共通知配置。
 *
 * 用于统一引擎中传递给 [com.example.pat.response.ResponseManager]，
 * 消除基础事件和自定义规则对通知设置的重复定义。
 *
 * @property enabled 是否发送通知
 * @property vibration 通知时是否震动
 * @property sound 通知时是否播放系统提示音
 * @property headsUp 是否以 Heads-up 横幅显示
 * @property lockScreenPublic 锁屏时是否显示通知内容
 */
data class NotificationPreference(
    val enabled: Boolean = true,
    val vibration: Boolean = false,
    val sound: Boolean = false,
    val headsUp: Boolean = true,
    val lockScreenPublic: Boolean = true
)
