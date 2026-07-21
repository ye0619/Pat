package com.example.pat.preset

import com.example.pat.event.EventType
import kotlin.random.Random

/**
 * 预设仓库 —— 管理所有内置预设反馈。
 *
 * 职责：
 * - 持有从 [PresetLoader] 加载的预设列表
 * - 按事件类型查询预设
 * - 提供随机选择能力（同一事件每次可返回不同反馈）
 *
 * 使用方式：
 * ```
 * val repo = PresetRepository(loader)
 * val randomPreset = repo.getRandom(EventType.SHAKE)
 * // 第一次: "别晃了~晃得我头都晕了"
 * // 第二次: "手这么抖，你不会是得了帕金森吧~"
 * ```
 *
 * 参考：Task 2 - 资源映射与随机返回
 */
class PresetRepository(
    private val loader: PresetLoader
) {
    /** 所有已加载的预设 */
    private val presets: List<PresetReaction> by lazy { loader.load() }

    /**
     * 获取指定事件类型的所有预设。
     *
     * @param eventType 事件类型
     * @return 该事件类型的所有预设（可能为空列表）
     */
    fun getByEventType(eventType: EventType): List<PresetReaction> {
        return presets.filter { it.eventType == eventType }
    }

    /**
     * 从指定事件类型的预设中随机选择一个。
     *
     * 同一事件类型每次调用可能返回不同预设，
     * 实现了"同一事件，第一次反馈A，第二次反馈B"的效果。
     *
     * @param eventType 事件类型
     * @return 随机选择的预设，该事件类型无预设时返回 null
     */
    fun getRandom(eventType: EventType): PresetReaction? {
        val list = getByEventType(eventType)
        if (list.isEmpty()) return null
        return list[Random.nextInt(list.size)]
    }

    /**
     * 获取所有具有预设的事件类型。
     */
    fun getAvailableEventTypes(): Set<EventType> {
        return presets.map { it.eventType }.toSet()
    }

    /**
     * 获取所有预设（主要用于 UI 展示）。
     */
    fun getAllPresets(): List<PresetReaction> = presets.toList()

    /**
     * 按事件类型分组的所有预设（主要用于 UI 展示）。
     *
     * @return Map<EventType, List<PresetReaction>>
     */
    fun getGroupedByEventType(): Map<EventType, List<PresetReaction>> {
        return presets.groupBy { it.eventType }
    }
}
