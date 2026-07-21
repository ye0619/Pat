package com.example.pat.engine

import com.example.pat.config.EventConfig
import com.example.pat.config.PreferenceManager
import com.example.pat.event.DeviceEvent
import com.example.pat.event.EventType

/**
 * 规则引擎 —— 匹配设备事件与用户配置。
 *
 * 职责：
 * - 将运行时 [DeviceEvent] 映射到 [EventType]
 * - 查找该事件类型对应的 [EventConfig]
 * - 检查阈值条件是否满足
 * - 检查事件类型是否启用
 *
 * 不包含：
 * - 事件收集（由 [EventDispatcher] 负责）
 * - 反馈执行（由 [ResponseManager] 负责）
 * - 配置持久化（由 [PreferenceManager] 负责）
 *
 * 参考文档：原始规范 2. 事件模型设计
 */
class RuleEngine(
    private val preferenceManager: PreferenceManager
) {
    /**
     * 评估一个设备事件，返回匹配的配置（如果有）。
     *
     * @param event 运行时设备事件
     * @return 匹配的 [EventConfig]，或 null（无匹配 / 未启用 / 阈值未满足）
     */
    fun evaluate(event: DeviceEvent): EventConfig? {
        // 1. 将 DeviceEvent 映射到 EventType
        val eventType = mapToEventType(event) ?: return null

        // 2. 查找用户配置
        val config = preferenceManager.getConfig(eventType) ?: return null

        // 3. 检查是否启用
        if (!config.enabled) return null

        // 4. 检查阈值条件
        if (!checkThreshold(event, config)) return null

        return config
    }

    /**
     * 将运行时事件映射为配置事件类型。
     */
    private fun mapToEventType(event: DeviceEvent): EventType? {
        return when (event) {
            is DeviceEvent.LongUsage -> EventType.SCREEN_LONG_USAGE
            is DeviceEvent.ChargeStart -> EventType.CHARGE_START
            is DeviceEvent.LowBattery -> EventType.LOW_BATTERY
            is DeviceEvent.Shake -> EventType.SHAKE
            is DeviceEvent.Impact -> EventType.IMPACT
            // 以下事件类型暂不作为可配置反馈事件
            is DeviceEvent.Drop,
            is DeviceEvent.ScreenWake,
            is DeviceEvent.ScreenOff,
            is DeviceEvent.BatteryFull,
            is DeviceEvent.LateNight -> null
        }
    }

    /**
     * 检查事件参数是否满足配置的阈值。
     */
    private fun checkThreshold(event: DeviceEvent, config: EventConfig): Boolean {
        return when (config.eventType) {
            EventType.SCREEN_LONG_USAGE -> {
                if (event is DeviceEvent.LongUsage) {
                    event.minutes >= config.threshold
                } else true
            }
            EventType.LOW_BATTERY -> {
                // 系统低电量广播已触发，阈值检查由 BatteryMonitor 保证
                true
            }
            EventType.CHARGE_START,
            EventType.SHAKE,
            EventType.IMPACT -> true // 无阈值
        }
    }
}
