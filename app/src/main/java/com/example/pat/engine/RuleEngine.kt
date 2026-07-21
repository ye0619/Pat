package com.example.pat.engine

import com.example.pat.data.EventConfigRepository
import com.example.pat.event.DeviceEvent
import com.example.pat.event.EventType
import com.example.pat.model.EventConfig

/**
 * 规则引擎 —— 匹配设备事件与用户配置的事件规则。
 *
 * 职责：
 * - 将运行时 [DeviceEvent] 映射到 [EventType]
 * - 从 [EventConfigRepository] 查找该事件类型对应的 [EventConfig]
 * - 检查阈值条件是否满足
 * - 检查事件类型是否启用
 *
 * 不包含：
 * - 事件收集（由 [EventDispatcher] 负责）
 * - 反馈执行（由 [ResponseManager] 负责）
 * - 配置持久化（由 [EventConfigRepository] 负责）
 */
class RuleEngine(
    private val configRepository: EventConfigRepository
) {
    /**
     * 评估一个设备事件，返回匹配的事件规则。
     */
    fun evaluate(event: DeviceEvent): EventConfig? {
        val eventType = mapToEventType(event) ?: return null
        val config = configRepository.getByEventType(eventType) ?: return null
        if (!config.enabled) return null
        if (!checkThreshold(event, config)) return null
        return config
    }

    /**
     * 获取指定事件类型的当前规则（供外部查询阈值等）。
     */
    fun getConfig(eventType: EventType): EventConfig? {
        return configRepository.getByEventType(eventType)
    }

    private fun mapToEventType(event: DeviceEvent): EventType? {
        return when (event) {
            is DeviceEvent.LongUsage -> EventType.SCREEN_LONG_USAGE
            is DeviceEvent.ChargeStart -> EventType.CHARGE_START
            is DeviceEvent.LowBattery -> EventType.LOW_BATTERY
            is DeviceEvent.Shake -> EventType.SHAKE
            is DeviceEvent.Impact -> EventType.IMPACT
            is DeviceEvent.Drop,
            is DeviceEvent.ScreenWake,
            is DeviceEvent.ScreenOff,
            is DeviceEvent.BatteryFull,
            is DeviceEvent.LateNight -> null
        }
    }

    private fun checkThreshold(event: DeviceEvent, config: EventConfig): Boolean {
        return when (config.eventType) {
            EventType.SCREEN_LONG_USAGE -> {
                if (event is DeviceEvent.LongUsage) event.minutes >= config.threshold
                else true
            }
            EventType.LOW_BATTERY -> true
            EventType.CHARGE_START,
            EventType.SHAKE,
            EventType.IMPACT -> true
        }
    }
}
