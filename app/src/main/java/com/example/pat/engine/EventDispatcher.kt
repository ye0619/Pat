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
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * 事件分发器 —— 从 EventBus 收集事件并分发到规则引擎。
 *
 * 职责：
 * - 从 [EventBus] 收集所有 [DeviceEvent]
 * - 应用去重和冷却逻辑（同一事件类型在冷却时间内不重复触发）
 * - 将事件转发给 [RuleEngine] 评估
 * - 将匹配的配置交给 [ResponseManager] 执行反馈
 *
 * 不包含：
 * - 事件检测（由 Monitor 层负责）
 * - 配置规则判断（由 [RuleEngine] 负责）
 * - 反馈执行（由 [ResponseManager] 负责）
 *
 * 参考文档：原始规范 4. 后台架构
 */
class EventDispatcher(
    private val ruleEngine: RuleEngine,
    private val responseManager: ResponseManager,
    private val presetRepository: PresetRepository,
    private val scope: CoroutineScope
) {
    /** 每个事件类型的冷却时间 (ms) */
    private val cooldownMs: Long = 10 * 60 * 1000L // 10分钟

    /** 记录每个事件类型最后一次触发的挂钟时间 */
    private val lastTriggerTime = mutableMapOf<String, Long>()

    /** 今日触发次数 */
    var todayTriggerCount: Int = 0
        private set

    /** 最近触发的事件列表（用于 UI 展示） */
    private val _recentTriggers = mutableListOf<RecentTrigger>()
    val recentTriggers: List<RecentTrigger> get() = _recentTriggers.toList()

    private var collectionJob: Job? = null

    /**
     * 最近一次触发的记录。
     */
    data class RecentTrigger(
        val eventTypeName: String,
        val displayText: String,
        val timestamp: Long
    )

    /**
     * 开始监听 EventBus 并分发事件。
     */
    fun start() {
        collectionJob?.cancel()
        collectionJob = scope.launch {
            EventBus.events.collect { event ->
                dispatch(event)
            }
        }
        Log.i(TAG, "EventDispatcher started")
    }

    /**
     * 停止监听。
     */
    fun stop() {
        collectionJob?.cancel()
        collectionJob = null
        Log.i(TAG, "EventDispatcher stopped")
    }

    // ══════════════════════════════════════════════════════════════
    // 内部分发逻辑
    // ══════════════════════════════════════════════════════════════

    /**
     * 处理单个事件：
     * 1. 检查冷却
     * 2. 规则评估
     * 3. 执行反馈
     */
    private fun dispatch(event: DeviceEvent) {
        Log.d(TAG, "Dispatching: ${event.toDisplayLabel()}")

        val config = ruleEngine.evaluate(event) ?: return

        // 检查冷却
        val eventKey = config.eventType.name
        val now = System.currentTimeMillis()
        val lastTime = lastTriggerTime[eventKey] ?: 0L
        if (now - lastTime < cooldownMs) {
            Log.d(TAG, "Event $eventKey is on cooldown (last=${now - lastTime}ms ago)")
            return
        }

        // 更新冷却时间
        lastTriggerTime[eventKey] = now

        // 增加今日触发计数
        todayTriggerCount++

        // 记录最近触发
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

        Log.i(TAG, "Rule matched: ${config.eventType.name} → \"$displayText\"")

        // 执行反馈
        responseManager.execute(config)
    }

    companion object {
        private const val TAG = "EventDispatcher"
    }
}
