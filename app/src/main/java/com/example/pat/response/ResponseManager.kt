package com.example.pat.response

import android.content.Context
import android.util.Log
import com.example.pat.audio.AudioPlayer
import com.example.pat.data.PresetRepository
import com.example.pat.model.AudioType
import com.example.pat.model.EventConfig
import com.example.pat.model.ReactionPreset

/**
 * 反馈管理器 —— 协调所有反馈通道。
 *
 * 关键保证：
 * 1. **文本与音频一致**：通知文本和播放的音频始终来自同一个预设
 * 2. **全局互斥**：同一时间只有一个事件在执行反馈，避免音频冲突
 *
 * 数据流：EventConfig.presetId → PresetRepository.getById() → ReactionPreset → 执行
 *
 * @property context Android Context
 * @property presetRepository 预设仓库
 */
class ResponseManager(
    private val context: Context,
    private val presetRepository: PresetRepository
) {
    private val notificationService = NotificationService(context)
    private val voiceService = VoiceService(context)
    private val audioPlayer = AudioPlayer(context)

    /** 全局反馈执行锁 —— 防止多个事件同时播放音频 */
    @Volatile
    private var isExecuting = false

    /**
     * 根据事件规则执行反馈。
     *
     * 统一解析一个 ReactionPreset，保证文本和音频来源一致：
     * 1. 优先使用 EventConfig.presetId 对应的预设
     * 2. 无预设时随机选择一个同事件类型的内置预设
     * 3. 仍无预设时使用系统默认文本（无音频）
     *
     * @param config 匹配的事件规则
     * @return 实际使用的反馈文本，被全局锁阻止时返回 null
     */
    fun execute(config: EventConfig): String? {
        // ── 全局互斥检查 ──
        if (isExecuting) {
            Log.i(TAG, "Another event is being processed — skipping ${config.eventType.name}")
            return null
        }
        synchronized(this) {
            if (isExecuting) return null
            isExecuting = true
        }

        try {
            return executeInternal(config)
        } finally {
            isExecuting = false
        }
    }

    /**
     * 内部执行逻辑 —— 文本和音频始终来自同一个预设。
     * @return 实际使用的反馈文本
     */
    private fun executeInternal(config: EventConfig): String {
        Log.i(TAG, "Executing response for: ${config.eventType.name} (presetId=${config.presetId})")

        // ── 解析预设（用户自定义优先） ──
        val preset: ReactionPreset? = resolvePreset(config)
            ?: presetRepository.getRandom(config.eventType)

        // 文本：用户自定义 > 预设 > 默认
        val displayText = config.effectiveText(preset)

        // 音频：用户自定义 > 预设
        val audioPath = config.effectiveAudioPath(preset)
        val audioType = preset?.audioType ?: AudioType.PRESET

        // 1. 通知反馈（Heads-up + 声音 + 震动，全部由用户偏好控制）
        if (config.notificationEnabled && displayText.isNotBlank()) {
            notificationService.show(
                title = "Pat",
                text = displayText,
                enableSound = config.soundEnabled,
                enableVibration = config.vibrationEnabled,
                showHeadsUp = config.showHeadsUp,
                lockScreenPublic = config.lockScreenPublic
            )
        }

        // 2. 语音反馈
        if (audioPath.isNotBlank()) {
            if (audioType == AudioType.CUSTOM && (audioPath.startsWith("/") || audioPath.startsWith(context.filesDir.absolutePath))) {
                voiceService.play(audioPath)
            } else {
                audioPlayer.playAsset(audioPath)
            }
        }

        Log.i(TAG, "Response executed: text=\"$displayText\" audio=\"$audioPath\"")
        return displayText
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

    /** 查询当前是否有事件正在执行 */
    val isBusy: Boolean get() = isExecuting

    companion object {
        private const val TAG = "ResponseManager"
    }
}
