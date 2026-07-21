package com.example.pat.response

import android.content.Context
import android.util.Log
import com.example.pat.audio.AudioPlayer
import com.example.pat.data.PresetRepository
import com.example.pat.model.EventConfig
import com.example.pat.model.ReactionPreset

/**
 * 反馈管理器 —— 协调所有反馈通道。
 *
 * 数据流：EventConfig.presetId → PresetRepository.getById() → ReactionPreset → 执行
 *
 * 职责：
 * - 根据 [EventConfig.presetId] 从 [PresetRepository] 加载 [ReactionPreset]
 * - 执行通知反馈（使用 ReactionPreset.text）
 * - 执行语音反馈（使用 ReactionPreset.audioAssetPath）
 * - 无预设时回退到系统默认文本
 *
 * 反馈优先级：
 * 1. EventConfig 绑定的 ReactionPreset（用户选择的内置或自定义预设）
 * 2. 随机选择一个同事件类型的内置预设
 * 3. EventConfig.defaultText（纯文本回退）
 *
 * 参考：目标架构 - ResponseManager
 */
class ResponseManager(
    private val context: Context,
    private val presetRepository: PresetRepository
) {
    private val notificationService = NotificationService(context)
    private val voiceService = VoiceService(context)
    private val audioPlayer = AudioPlayer(context)

    /**
     * 根据事件规则执行反馈。
     *
     * 流程：
     * 1. 通过 presetId 加载 ReactionPreset
     * 2. 通知：使用 preset.text 或随机预设或默认文本
     * 3. 语音：播放 preset.audioAssetPath 或随机预设音频
     *
     * @param config 匹配的事件规则
     */
    fun execute(config: EventConfig) {
        Log.i(TAG, "Executing response for: ${config.eventType.name} (presetId=${config.presetId})")

        // ── 解析预设 ──
        val selectedPreset = resolvePreset(config)

        // ── 确定反馈文本 ──
        val displayText = selectedPreset?.text
            ?: presetRepository.getRandom(config.eventType)?.text
            ?: EventConfig.defaultText(config.eventType)

        val audioPath = selectedPreset?.audioAssetPath
            ?: presetRepository.getRandom(config.eventType)?.audioAssetPath
            ?: ""

        // 1. 通知反馈
        if (config.notificationEnabled && displayText.isNotBlank()) {
            notificationService.show(
                title = "MotionPet",
                text = displayText
            )
        }

        // 2. 语音反馈
        if (audioPath.isNotBlank()) {
            if (selectedPreset?.audioType == com.example.pat.model.AudioType.CUSTOM) {
                // 用户自定义音频（可能是文件路径）
                voiceService.play(audioPath)
            } else {
                // 内置预设音频（assets 中）
                audioPlayer.playAsset(audioPath)
            }
        }
    }

    /**
     * 解析 presetId 获取 ReactionPreset。
     */
    private fun resolvePreset(config: EventConfig): ReactionPreset? {
        if (config.presetId.isBlank()) return null
        return presetRepository.getById(config.presetId)
    }

    /** 播放预设预览 */
    fun previewPresetAsset(assetPath: String) {
        audioPlayer.playAsset(assetPath)
    }

    /** 播放文件路径预览 */
    fun previewVoice(path: String) {
        voiceService.play(path)
    }

    /** 停止播放 */
    fun stopVoice() {
        voiceService.stop()
        audioPlayer.stop()
    }

    companion object {
        private const val TAG = "ResponseManager"
    }
}
