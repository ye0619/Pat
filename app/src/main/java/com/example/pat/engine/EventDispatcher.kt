package com.example.pat.engine

import android.util.Log
import com.example.pat.data.PresetRepository
import com.example.pat.event.DeviceEvent
import com.example.pat.event.EventBus
import com.example.pat.event.toDisplayLabel
import com.example.pat.model.EventConfig
import com.example.pat.response.ResponseManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 事件分发器 —— 从 EventBus 收集事件并分发到规则引擎。
 *
 * 职责：
 * - 从 [EventBus] 收集所有 [DeviceEvent]
 * - 应用**每事件类型独立冷却**（冷却时间来自 EventConfig.minIntervalMinutes）
 * - 检查**全局反馈锁**（ResponseManager.isBusy），避免多事件抢占音频
 * - 将事件转发给 [RuleEngine] 评估
 * - 将匹配的配置交给 [ResponseManager] 执行反馈
 */
class EventDispatcher(
    private val ruleEngine: RuleEngine,
    private val responseManager: ResponseManager,
    private val presetRepository: PresetRepository,
    private val scope: CoroutineScope
) {
    /** 记录每个事件类型最后一次触发的挂钟时间 */
    private val lastTriggerTime = mutableMapOf<String, Long>()

    /** 今日触发次数 */
    var todayTriggerCount: Int = 0
        private set

    /** 最近触发的事件列表（用于 UI 展示） */
    private val _recentTriggers = mutableListOf<RecentTrigger>()
    val recentTriggers: List<RecentTrigger> get() = _recentTriggers.toList()

    private var collectionJob: Job? = null

    data class RecentTrigger(
        val eventTypeName: String,
        val displayText: String,
        val timestamp: Long
    )

    fun start() {
        collectionJob?.cancel()
        collectionJob = scope.launch {
            EventBus.events.collect { event ->
                dispatch(event)
            }
        }
        Log.i(TAG, "EventDispatcher started")
    }

    fun stop() {
        collectionJob?.cancel()
        collectionJob = null
        Log.i(TAG, "EventDispatcher stopped")
    }

    /**
     * 处理单个事件：
     * 1. 规则评估
     * 2. 检查全局反馈锁（另一事件正在播放音频时跳过）
     * 3. 检查该事件类型的独立冷却时间（来自 EventConfig.minIntervalMinutes）
     * 4. 执行反馈
     */
    private fun dispatch(event: DeviceEvent) {
        Log.d(TAG, "Dispatching: ${event.toDisplayLabel()}")

        val config = ruleEngine.evaluate(event) ?: return

        // ── 检查全局反馈锁（避免音频冲突） ──
        if (responseManager.isBusy) {
            Log.d(TAG, "ResponseManager is busy — skipping ${config.eventType.name}")
            return
        }

        // ── 检查每事件独立冷却（来自用户配置） ──
        val eventKey = config.eventType.name
        val now = System.currentTimeMillis()
        val lastTime = lastTriggerTime[eventKey] ?: 0L
        val intervalMs = config.minIntervalMinutes * 60 * 1000L
        if (intervalMs > 0 && now - lastTime < intervalMs) {
            val remaining = (intervalMs - (now - lastTime)) / 1000
            Log.d(TAG, "Event $eventKey on cooldown (${remaining}s remaining, interval=${config.minIntervalMinutes}min)")
            return
        }

        // ── 更新冷却时间 ──
        lastTriggerTime[eventKey] = now

        // ── 增加计数 + 记录 ──
        todayTriggerCount++

        val displayText = presetRepository.getById(config.presetId)?.text
            ?: presetRepository.getRandom(config.eventType)?.text
            ?: EventConfig.defaultText(config.eventType)

        _recentTriggers.add(0, RecentTrigger(
            eventTypeName = EventConfig.displayName(config.eventType),
            displayText = displayText,
            timestamp = now
        ))
        if (_recentTriggers.size > 20) {
            _recentTriggers.removeAt(_recentTriggers.lastIndex)
        }

        Log.i(TAG, "Rule matched: ${config.eventType.name} → \"$displayText\" (interval=${config.minIntervalMinutes}min)")

        // ── 执行反馈 ──
        responseManager.execute(config)
    }

    companion object {
        private const val TAG = "EventDispatcher"
    }
}
