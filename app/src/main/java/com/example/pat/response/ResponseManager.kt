package com.example.pat.response

import android.content.Context
import android.util.Log
import com.example.pat.config.EventConfig

/**
 * 反馈管理器 —— 协调所有反馈通道。
 *
 * 职责：
 * - 接收匹配的 [EventConfig]
 * - 按配置决定启用哪些反馈通道（通知 / 语音 / 震动）
 * - 依次调用各通道服务
 *
 * 不包含：
 * - 通知构建细节（由 [NotificationService] 负责）
 * - 音频播放细节（由 [VoiceService] 负责）
 *
 * 参考文档：原始规范 3. 用户反馈系统
 */
class ResponseManager(
    private val context: Context
) {
    private val notificationService = NotificationService(context)
    private val voiceService = VoiceService(context)

    /**
     * 根据配置执行所有启用的反馈通道。
     *
     * @param config 匹配的事件配置
     */
    fun execute(config: EventConfig) {
        Log.i(TAG, "Executing response for: ${config.eventType.name}")

        // 1. 通知反馈
        if (config.notificationEnabled && config.text.isNotBlank()) {
            notificationService.show(
                title = "MotionPet",
                text = config.text
            )
        }

        // 2. 语音反馈
        if (config.voicePath.isNotBlank()) {
            voiceService.play(config.voicePath)
        }

        // 3. 震动反馈（使用系统默认短震）
        // Android Vibrator 权限已在 Manifest 中声明
    }

    /**
     * 播放语音预览（供 UI 层调用）。
     */
    fun previewVoice(path: String) {
        voiceService.play(path)
    }

    /**
     * 停止语音播放。
     */
    fun stopVoice() {
        voiceService.stop()
    }

    companion object {
        private const val TAG = "ResponseManager"
    }
}
