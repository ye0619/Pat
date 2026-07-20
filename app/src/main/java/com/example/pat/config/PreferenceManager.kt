package com.example.pat.config

import android.content.Context
import android.content.SharedPreferences
import com.example.pat.event.EventType
import org.json.JSONArray
import org.json.JSONObject

/**
 * 配置持久化管理器 —— 基于 SharedPreferences。
 *
 * 职责：
 * - 加载/保存所有 [EventConfig] 配置
 * - 首次启动时自动创建默认配置
 * - 以 JSON 格式序列化配置列表
 *
 * 使用方式：
 * ```
 * val manager = PreferenceManager(context)
 * val configs = manager.loadConfigs()
 * manager.saveConfig(updatedConfig)
 * ```
 *
 * 注意：第一版使用 SharedPreferences，不使用数据库。
 * 后续可迁移到 DataStore 获取协程原生支持。
 */
class PreferenceManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 加载所有事件配置。
     * 首次调用时自动创建默认配置。
     */
    fun loadConfigs(): List<EventConfig> {
        val json = prefs.getString(KEY_CONFIGS, null)
        if (json == null) {
            val defaults = createDefaults()
            saveConfigs(defaults)
            return defaults
        }
        return parseConfigs(json)
    }

    /**
     * 保存单个配置（按 id 匹配更新，无匹配则追加）。
     */
    fun saveConfig(config: EventConfig) {
        val configs = loadConfigs().toMutableList()
        val index = configs.indexOfFirst { it.id == config.id }
        if (index >= 0) {
            configs[index] = config
        } else {
            configs.add(config.copy(id = generateId()))
        }
        saveConfigs(configs)
    }

    /**
     * 获取某个事件类型的当前配置。
     */
    fun getConfig(eventType: EventType): EventConfig? {
        return loadConfigs().find { it.eventType == eventType }
    }

    // ══════════════════════════════════════════════════════════════
    // 内部实现
    // ══════════════════════════════════════════════════════════════

    /** 批量保存配置 */
    private fun saveConfigs(configs: List<EventConfig>) {
        val json = serializeConfigs(configs)
        prefs.edit().putString(KEY_CONFIGS, json).apply()
    }

    /** 创建默认配置（首次启动时使用） */
    private fun createDefaults(): List<EventConfig> {
        return EventType.entries.map { type ->
            EventConfig(
                id = generateId(),
                eventType = type,
                enabled = true,
                threshold = EventConfig.defaultThreshold(type),
                text = EventConfig.defaultText(type),
                voicePath = "",
                notificationEnabled = true
            )
        }
    }

    /** JSON 序列化 */
    private fun serializeConfigs(configs: List<EventConfig>): String {
        val array = JSONArray()
        configs.forEach { config ->
            val obj = JSONObject().apply {
                put("id", config.id)
                put("eventType", config.eventType.name)
                put("enabled", config.enabled)
                put("threshold", config.threshold)
                put("text", config.text)
                put("voicePath", config.voicePath)
                put("notificationEnabled", config.notificationEnabled)
            }
            array.put(obj)
        }
        return array.toString()
    }

    /** JSON 反序列化 */
    private fun parseConfigs(json: String): List<EventConfig> {
        val array = JSONArray(json)
        val configs = mutableListOf<EventConfig>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val typeName = obj.optString("eventType", "")
            val eventType = try {
                EventType.valueOf(typeName)
            } catch (e: IllegalArgumentException) {
                continue // 跳过未知类型
            }
            configs.add(
                EventConfig(
                    id = obj.optString("id", ""),
                    eventType = eventType,
                    enabled = obj.optBoolean("enabled", true),
                    threshold = obj.optInt("threshold", EventConfig.defaultThreshold(eventType)),
                    text = obj.optString("text", EventConfig.defaultText(eventType)),
                    voicePath = obj.optString("voicePath", ""),
                    notificationEnabled = obj.optBoolean("notificationEnabled", true)
                )
            )
        }
        return configs
    }

    /** 生成唯一 ID */
    private fun generateId(): String = java.util.UUID.randomUUID().toString()

    companion object {
        private const val PREFS_NAME = "motionpet_config"
        private const val KEY_CONFIGS = "event_configs"
    }
}
