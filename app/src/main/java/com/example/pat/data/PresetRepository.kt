package com.example.pat.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.pat.event.EventType
import com.example.pat.model.AudioType
import com.example.pat.model.ReactionPreset
import com.example.pat.preset.PresetLoader
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 预设仓库 —— 管理所有 ReactionPreset（内置 + 自定义）。
 *
 * 职责：
 * - 加载内置预设（通过 PresetLoader 从 assets/ 扫描）
 * - 管理用户自定义预设（持久化到 SharedPreferences）
 * - 按 ID / 事件类型查询预设
 * - 支持随机选择（同事件不同反馈）
 *
 * 两层存储：
 * 1. 内置预设：运行时从 assets/ 扫描，不可变
 * 2. 自定义预设：用户创建，保存到 SharedPreferences
 *
 * 参考：目标架构 - PresetRepository
 */
class PresetRepository(
    context: Context
) {
    private val loader = PresetLoader(context)
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 内置预设（延迟加载） */
    private val builtInPresets: List<ReactionPreset> by lazy { loader.load() }

    /** 自定义预设缓存（null = 未加载）。save/delete 后自动刷新 */
    @Volatile
    private var _customPresets: List<ReactionPreset>? = null

    private val customPresets: List<ReactionPreset>
        get() {
            var cached = _customPresets
            if (cached == null) {
                cached = loadCustomPresets()
                _customPresets = cached
            }
            return cached
        }

    // ══════════════════════════════════════════════════════════════
    // 查询 API
    // ══════════════════════════════════════════════════════════════

    /** 获取所有预设（内置 + 自定义） */
    fun getAll(): List<ReactionPreset> = builtInPresets + customPresets

    /** 获取所有内置预设 */
    fun getBuiltIn(): List<ReactionPreset> = builtInPresets.toList()

    /** 获取所有自定义预设 */
    fun getCustom(): List<ReactionPreset> = customPresets.toList()

    /** 按 ID 查找预设 */
    fun getById(id: String): ReactionPreset? {
        if (id.isBlank()) return null
        return getAll().find { it.id == id }
    }

    /** 获取指定事件类型的所有预设（内置 + 关联的自定义） */
    fun getByEventType(eventType: EventType): List<ReactionPreset> {
        return getAll().filter {
            it.eventType == eventType || it.eventType == null
        }
    }

    /** 分组查询（按事件类型） */
    fun getGroupedByEventType(): Map<EventType, List<ReactionPreset>> {
        return getAll().groupBy { it.eventType ?: EventType.SHAKE }
            .filterKeys { it != EventType.SHAKE || getAll().any { p -> p.eventType == null } }
            .let { map ->
                // 将 eventType=null 的通用预设附加到所有事件类型
                val generic = getAll().filter { it.eventType == null }
                if (generic.isEmpty()) return@let map
                val result = mutableMapOf<EventType, List<ReactionPreset>>()
                EventType.entries.forEach { type ->
                    result[type] = (map[type] ?: emptyList()) + generic
                }
                result
            }
    }

    /** 随机选择 */
    fun getRandom(eventType: EventType): ReactionPreset? {
        val list = getByEventType(eventType)
        if (list.isEmpty()) return null
        return list[kotlin.random.Random.nextInt(list.size)]
    }

    // ══════════════════════════════════════════════════════════════
    // 自定义预设管理
    // ══════════════════════════════════════════════════════════════

    /**
     * 保存自定义预设（新建或更新）。
     */
    fun saveCustom(preset: ReactionPreset) {
        val list = loadCustomPresets().toMutableList()
        val index = list.indexOfFirst { it.id == preset.id }
        if (index >= 0) {
            list[index] = preset
        } else {
            list.add(preset.copy(id = if (preset.id.isBlank()) UUID.randomUUID().toString() else preset.id))
        }
        persistCustomPresets(list)
        _customPresets = list // 刷新缓存
        Log.i(TAG, "Custom preset saved: id=${preset.id} name=${preset.name}")
    }

    /**
     * 删除自定义预设。
     */
    fun deleteCustom(presetId: String) {
        val list = loadCustomPresets().filter { it.id != presetId }
        persistCustomPresets(list)
        _customPresets = list // 刷新缓存
        Log.i(TAG, "Custom preset deleted: $presetId")
    }

    // ══════════════════════════════════════════════════════════════
    // 内部：自定义预设持久化
    // ══════════════════════════════════════════════════════════════

    private fun loadCustomPresets(): List<ReactionPreset> {
        val json = prefs.getString(KEY_CUSTOM_PRESETS, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                val eventTypeName = obj.optString("eventType", "")
                ReactionPreset(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    text = obj.getString("text"),
                    audioAssetPath = obj.optString("audioAssetPath", ""),
                    audioType = try {
                        AudioType.valueOf(obj.optString("audioType", "CUSTOM"))
                    } catch (e: IllegalArgumentException) { AudioType.CUSTOM },
                    eventType = try {
                        if (eventTypeName.isNotBlank()) EventType.valueOf(eventTypeName) else null
                    } catch (e: IllegalArgumentException) { null }
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse custom presets", e)
            emptyList()
        }
    }

    private fun persistCustomPresets(list: List<ReactionPreset>) {
        val array = JSONArray()
        list.forEach { preset ->
            array.put(JSONObject().apply {
                put("id", preset.id)
                put("name", preset.name)
                put("text", preset.text)
                put("audioAssetPath", preset.audioAssetPath)
                put("audioType", preset.audioType.name)
                put("eventType", preset.eventType?.name ?: "")
            })
        }
        prefs.edit().putString(KEY_CUSTOM_PRESETS, array.toString()).apply()
    }

    companion object {
        private const val TAG = "PresetRepository"
        private const val PREFS_NAME = "motionpet_presets"
        private const val KEY_CUSTOM_PRESETS = "custom_presets"
    }
}
