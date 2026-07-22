package com.example.pat.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.pat.event.AtomicEventType
import com.example.pat.event.EventType
import com.example.pat.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 事件定义仓库 —— 管理 EventDefinition 的持久化。
 * 替换旧的 UserRuleRepository。统一管理自定义事件定义。
 */
class EventDefinitionRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadAll(): List<EventDefinition> {
        val json = prefs.getString(KEY_DEFINITIONS, null) ?: return emptyList()
        return parse(json)
    }

    fun save(def: EventDefinition) {
        val defs = loadAll().toMutableList()
        val index = defs.indexOfFirst { it.id == def.id }
        if (index >= 0) defs[index] = def
        else defs.add(def.copy(id = if (def.id.isBlank()) generateId() else def.id))
        saveAll(defs)
        Log.i(TAG, "EventDefinition saved: id=${def.id} name=${def.name}")
    }

    fun delete(defId: String) {
        saveAll(loadAll().filter { it.id != defId })
        Log.i(TAG, "EventDefinition deleted: $defId")
    }

    fun count(): Int = loadAll().size

    /** 冲突检测：查找与指定定义使用了相同原子条件类型的其他启用事件 */
    fun findConflicts(def: EventDefinition): List<EventDefinition> {
        val types = def.conditionGroups.flatMap { it.conditions }.map { it.atomicType }.toSet()
        return loadAll().filter { other ->
            other.id != def.id && other.enabled &&
            other.conditionGroups.flatMap { it.conditions }.any { it.atomicType in types }
        }
    }

    private fun saveAll(defs: List<EventDefinition>) {
        prefs.edit().putString(KEY_DEFINITIONS, serialize(defs)).apply()
    }

    private fun serialize(defs: List<EventDefinition>): String = JSONArray().apply {
        defs.forEach { def -> put(JSONObject().apply {
            put("id", def.id); put("name", def.name); put("enabled", def.enabled)
            put("isPreset", def.isPreset)
            def.eventType?.let { put("eventType", it.name) }
            put("triggerType", def.triggerType.name)
            put("timeWindowMs", def.timeWindowMs)
            put("minIntervalMinutes", def.minIntervalMinutes)
            put("notifEnabled", def.notification.enabled)
            put("headsUp", def.notification.headsUp)
            put("playFeedbackAudio", def.notification.playFeedbackAudio)
            put("vibration", def.notification.vibration)
            put("lockScreen", def.notification.lockScreen)
            put("conditionGroups", JSONArray().apply {
                def.conditionGroups.forEach { g -> put(JSONObject().apply {
                    put("conditions", JSONArray().apply {
                        g.conditions.forEach { c -> put(JSONObject().apply {
                            put("atomicType", c.atomicType.name)
                            c.operator?.let { put("operator", it.name) }
                            c.value?.let { put("value", it) }
                            put("count", c.count)
                            c.valueMin?.let { put("valueMin", it) }
                            c.valueMax?.let { put("valueMax", it) }
                            if (c.checkCurrentState) put("checkCurrentState", true)
                        })}
                    })
                })}
            })
            put("reactions", JSONArray().apply {
                def.reactions.forEach { r -> put(JSONObject().apply {
                    put("text", r.text); put("audioPath", r.audioPath)
                })}
            })
        })}
    }.toString()

    private fun parse(json: String): List<EventDefinition> {
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            val groupsArray = obj.optJSONArray("conditionGroups") ?: JSONArray()
            val groups = (0 until groupsArray.length()).map { gi ->
                val gObj = groupsArray.getJSONObject(gi)
                val cArr = gObj.optJSONArray("conditions") ?: JSONArray()
                ConditionGroup((0 until cArr.length()).map { ci ->
                    val c = cArr.getJSONObject(ci)
                    ConditionDef(
                        atomicType = try { AtomicEventType.valueOf(c.getString("atomicType")) }
                            catch (e: Exception) { AtomicEventType.SHAKE },
                        operator = c.optString("operator", "").let {
                            try { ConditionDef.CompareOp.valueOf(it) } catch (e: Exception) { null } },
                        value = if (c.has("value")) c.optInt("value") else null,
                        count = c.optInt("count", 1),
                        valueMin = if (c.has("valueMin")) c.optInt("valueMin") else null,
                        valueMax = if (c.has("valueMax")) c.optInt("valueMax") else null,
                        checkCurrentState = c.optBoolean("checkCurrentState", false)
                    )
                })
            }
            val rArr = obj.optJSONArray("reactions") ?: JSONArray()
            val reactions = (0 until rArr.length()).map { ri ->
                val ro = rArr.getJSONObject(ri)
                ReactionItem(ro.optString("text", ""), ro.optString("audioPath", ""))
            }
            EventDefinition(
                id = obj.optString("id", ""), name = obj.optString("name", ""),
                enabled = obj.optBoolean("enabled", false),
                isPreset = obj.optBoolean("isPreset", false),
                eventType = obj.optString("eventType", "").let {
                    try { EventType.valueOf(it) } catch (e: Exception) { null } },
                triggerType = try { TriggerType.valueOf(obj.optString("triggerType", "SINGLE")) }
                    catch (e: Exception) { TriggerType.SINGLE },
                conditionGroups = groups, timeWindowMs = obj.optLong("timeWindowMs", 5000L),
                reactions = reactions,
                notification = NotificationConfig(
                    enabled = obj.optBoolean("notifEnabled", false),
                    headsUp = obj.optBoolean("headsUp", false),
                    playFeedbackAudio = obj.optBoolean("playFeedbackAudio", false),
                    vibration = obj.optBoolean("vibration", false),
                    lockScreen = obj.optBoolean("lockScreen", false)
                ),
                minIntervalMinutes = obj.optInt("minIntervalMinutes", 120)
            )
        }
    }

    private fun generateId(): String = UUID.randomUUID().toString()

    companion object {
        private const val TAG = "EventDefinitionRepo"
        private const val PREFS_NAME = "motionpet_event_defs"
        private const val KEY_DEFINITIONS = "event_definitions_v1"
    }
}
