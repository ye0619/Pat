package com.example.pat.response

import android.content.Context
import android.util.Log
import com.example.pat.audio.AudioPlayer
import com.example.pat.data.PresetRepository
import com.example.pat.model.AudioType
import com.example.pat.model.EventConfig
import com.example.pat.model.NotificationConfig
import com.example.pat.model.ReactionItem
import com.example.pat.model.ReactionPreset

/**
 * 反馈管理器 —— 协调所有反馈通道。
 *
 * 关键保证：
 * 1. **文本与音频一致**：通知文本和播放的音频始终来自同一个 ReactionItem
 * 2. **全局互斥**：同一时间只有一个事件在执行反馈，避免音频冲突
 * 3. **反应池支持**：支持多个 ReactionItem，触发时随机选择
 *
 * v2 改进：
 * - 新增 [execute(reactions, notification)] 方法，支持反应池
 * - 旧的 [execute(config)] 方法保留兼容，内部转为新接口
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

    // ══════════════════════════════════════════════════════════════
    // v2: 反应池接口（推荐）
    // ══════════════════════════════════════════════════════════════

    /**
     * 从反应池中随机选择一个 ReactionItem 并执行反馈。
     *
     * @param reactions 反馈池（至少 1 个）
     * @param notification 通知偏好
     * @return 实际使用的反馈文本，被全局锁阻止时返回 null
     */
    fun execute(reactions: List<ReactionItem>, notification: NotificationConfig): String? {
        if (reactions.isEmpty()) {
            Log.w(TAG, "Empty reaction pool — nothing to execute")
            return null
        }

        // ── 全局互斥检查 ──
        if (isExecuting) {
            Log.i(TAG, "Another event is being processed — skipping")
            return null
        }
        synchronized(this) {
            if (isExecuting) return null
            isExecuting = true
        }

        try {
            // 随机选择一个 ReactionItem
            val selected = if (reactions.size == 1) {
                reactions.first()
            } else {
                reactions[kotlin.random.Random.nextInt(reactions.size)]
            }

            return executeReaction(selected, notification)
        } finally {
            isExecuting = false
        }
    }

    /**
     * 执行单个 ReactionItem 的反馈。
     */
    private fun executeReaction(item: ReactionItem, notification: NotificationConfig): String {
        val displayText = item.text
        val audioPath = item.audioPath

        Log.i(TAG, "Executing reaction: text=\"$displayText\" audio=\"$audioPath\"")

        // 1. 通知反馈
        val showNotif = notification.headsUp || notification.lockScreen
                || notification.playFeedbackAudio || notification.vibration
        if (showNotif && displayText.isNotBlank()) {
            notificationService.show(
                title = "Pat",
                text = displayText,
                enableSound = notification.playFeedbackAudio,
                enableVibration = notification.vibration,
                showHeadsUp = notification.headsUp,
                lockScreenPublic = notification.lockScreen
            )
        }

        // 2. 语音反馈
        if (audioPath.isNotBlank()) {
            if (audioPath.startsWith("/") || audioPath.startsWith(context.filesDir.absolutePath)) {
                voiceService.play(audioPath)
            } else {
                audioPlayer.playAsset(audioPath)
            }
        }

        return displayText
    }

    // ══════════════════════════════════════════════════════════════
    // 旧版接口（向后兼容）
    // ══════════════════════════════════════════════════════════════

    /**
     * 根据事件规则执行反馈（旧接口，内部转为新接口）。
     *
     * @param config 匹配的事件规则
     * @return 实际使用的反馈文本，被全局锁阻止时返回 null
     */
    fun execute(config: EventConfig): String? {
        // 解析预设
        val preset: ReactionPreset? = resolvePreset(config)
            ?: presetRepository.getRandom(config.eventType)

        val text = config.effectiveText(preset)
        val audioPath = config.effectiveAudioPath(preset)

        return execute(
            reactions = listOf(ReactionItem(text = text, audioPath = audioPath)),
            notification = NotificationConfig(
                enabled = config.notificationEnabled,
                headsUp = config.showHeadsUp,
                playFeedbackAudio = config.soundEnabled,
                vibration = config.vibrationEnabled,
                lockScreen = config.lockScreenPublic
            )
        )
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
