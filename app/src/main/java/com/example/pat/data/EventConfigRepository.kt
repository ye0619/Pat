package com.example.pat.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.pat.event.EventType
import com.example.pat.model.EventConfig
import com.example.pat.model.ReactionItem
import org.json.JSONArray
import org.json.JSONObject

/**
 * 事件配置仓库 —— 管理 EventConfig 的持久化。
 *
 * 职责：
 * - 加载/保存所有 [EventConfig] 规则
 * - 首次启动时自动创建默认规则（绑定到内置预设）
 * - 以 JSON 格式序列化到 SharedPreferences
 *
 * @property presetRepository 用于自动关联默认预设
 */
class EventConfigRepository(
    context: Context,
    private val presetRepository: PresetRepository
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 加载所有事件规则。
     * 首次调用时自动创建默认规则。
     */
    fun loadAll(): List<EventConfig> {
        val json = prefs.getString(KEY_CONFIGS, null)
        val configs = if (json == null) {
            createDefaults()
        } else {
            parse(json)
        }
        // 确保所有 EventType 都有对应配置（补充缺失的）
        val result = configs.toMutableList()
        val existingTypes = result.map { it.eventType }.toSet()
        EventType.entries.forEach { type ->
            if (type !in existingTypes) {
                val firstPreset = presetRepository.getByEventType(type).firstOrNull()
                result.add(EventConfig(
                    id = generateId(), eventType = type, enabled = true,
                    threshold = EventConfig.defaultThreshold(type),
                    presetId = firstPreset?.id ?: "",
                    notificationEnabled = true, minIntervalMinutes = 120,
                    vibrationEnabled = false, soundEnabled = false,
                    showHeadsUp = true, lockScreenPublic = true
                ))
            }
        }
        if (result.size != configs.size) saveAll(result)
        return result
    }

    /**
     * 保存单个规则（按 id 匹配更新，无匹配则追加）。
     */
    fun save(config: EventConfig) {
        val configs = loadAll().toMutableList()
        val index = configs.indexOfFirst { it.id == config.id }
        if (index >= 0) {
            configs[index] = config
        } else {
            configs.add(config.copy(id = generateId()))
        }
        saveAll(configs)
    }

    /**
     * 获取某个事件类型的当前规则。
     */
    fun getByEventType(eventType: EventType): EventConfig? {
        return loadAll().find { it.eventType == eventType }
    }

    // ══════════════════════════════════════════════════════════════
    // 内部
    // ══════════════════════════════════════════════════════════════

    private fun saveAll(configs: List<EventConfig>) {
        val json = serialize(configs)
        prefs.edit().putString(KEY_CONFIGS, json).apply()
    }

    /**
     * 创建默认规则 —— 每个 EventType 一条，自动绑定第一个内置预设。
     */
    fun restoreDefaults() { createDefaults().forEach { save(it) } }
    fun restoreSingle(eventType: EventType) { createDefaults().find { it.eventType == eventType }?.let { save(it) } }
    private fun createDefaults(): List<EventConfig> {
        return EventType.entries.map { type ->
            val firstPreset = presetRepository.getByEventType(type).firstOrNull()
            EventConfig(
                id = generateId(),
                eventType = type,
                enabled = true,
                threshold = EventConfig.defaultThreshold(type),
                presetId = firstPreset?.id ?: "",
                notificationEnabled = true,
                minIntervalMinutes = 10,
                vibrationEnabled = false,
                soundEnabled = false,
                showHeadsUp = true,
                lockScreenPublic = true,
                reactions = emptyList()  // 默认无反应池
            )
        }
    }

    private fun serialize(configs: List<EventConfig>): String {
        val array = JSONArray()
        configs.forEach { config ->
            array.put(JSONObject().apply {
                put("id", config.id)
                put("eventType", config.eventType.name)
                put("enabled", config.enabled)
                put("threshold", config.threshold)
                put("presetId", config.presetId)
                put("notificationEnabled", config.notificationEnabled)
                put("minIntervalMinutes", config.minIntervalMinutes)
                put("priority", config.priority)
                put("vibrationEnabled", config.vibrationEnabled)
                put("soundEnabled", config.soundEnabled)
                put("showHeadsUp", config.showHeadsUp)
                put("lockScreenPublic", config.lockScreenPublic)
                put("customText", config.customText)
                put("customAudioPath", config.customAudioPath)
                // v2: 反应池
                put("reactions", JSONArray().apply {
                    config.reactions.forEach { item ->
                        put(JSONObject().apply {
                            put("text", item.text)
                            put("audioPath", item.audioPath)
                        })
                    }
                })
            })
        }
        return array.toString()
    }

    private fun parse(json: String): List<EventConfig> {
        val array = JSONArray(json)
        val configs = mutableListOf<EventConfig>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val typeName = obj.optString("eventType", "")
            val eventType = try {
                EventType.valueOf(typeName)
            } catch (e: IllegalArgumentException) {
                // 迁移：IMPACT 已重命名为 DROP
                if (typeName == "IMPACT") EventType.DROP
                else continue
            }

            // v2: 解析反应池
            val reactions = mutableListOf<ReactionItem>()
            val reactionsArray = obj.optJSONArray("reactions")
            if (reactionsArray != null) {
                for (j in 0 until reactionsArray.length()) {
                    val rObj = reactionsArray.getJSONObject(j)
                    reactions.add(ReactionItem(
                        text = rObj.optString("text", ""),
                        audioPath = rObj.optString("audioPath", "")
                    ))
                }
            }

            configs.add(
                EventConfig(
                    id = obj.optString("id", ""),
                    eventType = eventType,
                    enabled = obj.optBoolean("enabled", true),
                    threshold = obj.optInt("threshold", EventConfig.defaultThreshold(eventType)),
                    presetId = obj.optString("presetId", ""),
                    notificationEnabled = obj.optBoolean("notificationEnabled", true),
                    minIntervalMinutes = obj.optInt("minIntervalMinutes", 10),
                    priority = obj.optInt("priority", 5),
                    vibrationEnabled = if (obj.has("vibrationEnabled"))
                        obj.optBoolean("vibrationEnabled") else false,
                    soundEnabled = if (obj.has("soundEnabled"))
                        obj.optBoolean("soundEnabled") else false,
                    showHeadsUp = if (obj.has("showHeadsUp"))
                        obj.optBoolean("showHeadsUp") else true,
                    lockScreenPublic = if (obj.has("lockScreenPublic"))
                        obj.optBoolean("lockScreenPublic") else true,
                    customText = obj.optString("customText", ""),
                    customAudioPath = obj.optString("customAudioPath", ""),
                    reactions = reactions
                )
            )
        }
        return configs
    }

    private fun generateId(): String = java.util.UUID.randomUUID().toString()

    companion object {
        private const val TAG = "EventConfigRepository"
        private const val PREFS_NAME = "motionpet_event_rules"
        private const val KEY_CONFIGS = "event_configs_v2"
    }
}
