package com.example.pat.response

import android.content.Context
import android.util.Log
import com.example.pat.audio.AudioPlayer
import com.example.pat.config.EventConfig
import com.example.pat.preset.PresetRepository

/**
 * 反馈管理器 —— 协调所有反馈通道。
 *
 * 职责：
 * - 接收匹配的 [EventConfig]
 * - 按配置决定启用哪些反馈通道（通知 / 语音 / 震动）
 * - 依次调用各通道服务
 * - 当用户未上传自定义语音时，从 [PresetRepository] 随机选择预设反馈
 *
 * 反馈优先级：
 * 1. 用户上传的自定义音频 → [VoiceService] 播放
 * 2. 内置预设音频（随机） → [AudioPlayer] 播放
 * 3. 仅文本通知（无音频资源时）
 *
 * 不包含：
 * - 通知构建细节（由 [NotificationService] 负责）
 * - 音频播放细节（由 [VoiceService] / [AudioPlayer] 负责）
 *
 * 参考文档：Task 5 - 连接 Response 系统
 */
class ResponseManager(
    private val context: Context,
    private val presetRepository: PresetRepository? = null
) {
    private val notificationService = NotificationService(context)
    private val voiceService = VoiceService(context)
    private val audioPlayer = AudioPlayer(context)

    /**
     * 根据配置执行所有启用的反馈通道。
     *
     * 流程：
     * 1. 通知反馈（使用配置文本或预设文本）
     * 2. 语音反馈（用户自定义音频 > 预设音频随机选择）
     *
     * @param config 匹配的事件配置
     */
    fun execute(config: EventConfig) {
        Log.i(TAG, "Executing response for: ${config.eventType.name}")

        // ── 尝试获取预设（用于文本和音频回退） ──
        val preset = presetRepository?.getRandom(config.eventType)

        // ── 确定反馈文本 ──
        val displayText = when {
            config.text.isNotBlank() -> config.text          // 用户自定义文本
            preset != null -> preset.displayText             // 预设文本
            else -> EventConfig.defaultText(config.eventType) // 系统默认文本
        }

        // 1. 通知反馈
        if (config.notificationEnabled && displayText.isNotBlank()) {
            notificationService.show(
                title = "MotionPet",
                text = displayText
            )
        }

        // 2. 语音反馈
        when {
            // 用户上传了自定义音频
            config.voicePath.isNotBlank() -> {
                voiceService.play(config.voicePath)
            }
            // 使用内置预设音频（随机选择）
            preset != null -> {
                audioPlayer.playAsset(preset.audioAssetPath)
            }
        }

        // 3. 震动反馈（使用系统默认短震）
        // Android Vibrator 权限已在 Manifest 中声明
    }

    /**
     * 播放语音预览（供 UI 层调用）。
     *
     * @param path 音频文件的绝对路径
     */
    fun previewVoice(path: String) {
        voiceService.play(path)
    }

    /**
     * 播放预设音频预览（供 UI 层调用）。
     *
     * @param assetPath assets 中的音频文件路径
     */
    fun previewPresetAsset(assetPath: String) {
        audioPlayer.playAsset(assetPath)
    }

    /**
     * 停止语音播放。
     */
    fun stopVoice() {
        voiceService.stop()
        audioPlayer.stop()
    }

    companion object {
        private const val TAG = "ResponseManager"
    }
}
