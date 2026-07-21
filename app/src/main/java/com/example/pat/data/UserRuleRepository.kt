package com.example.pat.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.pat.event.AtomicEventType
import com.example.pat.model.ConditionClause
import com.example.pat.model.ConditionOperator
import com.example.pat.model.ConflictStrategy
import com.example.pat.model.UserRule
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 用户规则仓库 —— 管理 [UserRule] 的持久化。
 *
 * 职责：
 * - 加载/保存所有 UserRule
 * - 按事件类型查询相关规则
 * - 以 JSON 格式序列化到 SharedPreferences
 *
 * 与 [EventConfigRepository] 使用相同的存储模式，
 * 但数据存储在独立的 SharedPreferences 文件中。
 */
class UserRuleRepository(
    context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 加载所有用户规则。
     */
    fun loadAll(): List<UserRule> {
        val json = prefs.getString(KEY_RULES, null) ?: return emptyList()
        return parse(json)
    }

    /**
     * 保存单个规则（按 id 匹配更新，无匹配则追加）。
     */
    fun save(rule: UserRule) {
        val rules = loadAll().toMutableList()
        val index = rules.indexOfFirst { it.id == rule.id }
        val saved = if (index >= 0) {
            rules[index] = rule
            rule
        } else {
            val newRule = rule.copy(id = generateId())
            rules.add(newRule)
            newRule
        }
        saveAll(rules)
        Log.i(TAG, "Rule saved: id=${saved.id} name=${saved.name}")
    }

    /**
     * 删除一条规则。
     */
    fun delete(ruleId: String) {
        val rules = loadAll().filter { it.id != ruleId }
        saveAll(rules)
        Log.i(TAG, "Rule deleted: $ruleId")
    }

    /**
     * 查找所有与指定事件类型相关的规则（条件中包含该事件类型）。
     */
    fun findByEventType(eventType: AtomicEventType): List<UserRule> {
        return loadAll().filter { rule ->
            rule.conditions.any { it.eventType == eventType }
        }
    }

    /**
     * 获取规则总数。
     */
    fun count(): Int = loadAll().size

    // ══════════════════════════════════════════════════════════════
    // 内部
    // ══════════════════════════════════════════════════════════════

    private fun saveAll(rules: List<UserRule>) {
        val json = serialize(rules)
        prefs.edit().putString(KEY_RULES, json).apply()
    }

    private fun serialize(rules: List<UserRule>): String {
        val array = JSONArray()
        rules.forEach { rule ->
            array.put(JSONObject().apply {
                put("id", rule.id)
                put("name", rule.name)
                put("operator", rule.operator.name)
                put("timeWindowMs", rule.timeWindowMs)
                put("priority", rule.priority)
                put("enabled", rule.enabled)
                put("reactionPresetId", rule.reactionPresetId)
                put("notificationEnabled", rule.notificationEnabled)
                put("vibrationEnabled", rule.vibrationEnabled)
                put("soundEnabled", rule.soundEnabled)
                put("showHeadsUp", rule.showHeadsUp)
                put("lockScreenPublic", rule.lockScreenPublic)
                put("minIntervalMinutes", rule.minIntervalMinutes)
                put("conflictStrategy", rule.conflictStrategy.name)
                // 条件列表
                put("conditions", JSONArray().apply {
                    rule.conditions.forEach { clause ->
                        put(JSONObject().apply {
                            put("eventType", clause.eventType.name)
                            clause.operator?.let { put("compareOp", it.name) }
                            clause.value?.let { put("value", it) }
                            put("count", clause.count)
                        })
                    }
                })
            })
        }
        return array.toString()
    }

    @Suppress("UNCHECKED_CAST")
    private fun parse(json: String): List<UserRule> {
        val array = JSONArray(json)
        val rules = mutableListOf<UserRule>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val conditionsArray = obj.optJSONArray("conditions") ?: JSONArray()
            val conditions = (0 until conditionsArray.length()).map { j ->
                val c = conditionsArray.getJSONObject(j)
                val eventTypeName = c.getString("eventType")
                val eventType = try {
                    AtomicEventType.valueOf(eventTypeName)
                } catch (e: IllegalArgumentException) {
                    AtomicEventType.SHAKE // fallback
                }
                ConditionClause(
                    eventType = eventType,
                    operator = c.optString("compareOp", "").let { op ->
                        try { ConditionClause.CompareOp.valueOf(op) } catch (e: IllegalArgumentException) { null }
                    },
                    value = if (c.has("value")) c.optInt("value") else null,
                    count = c.optInt("count", 1)
                )
            }

            rules.add(
                UserRule(
                    id = obj.optString("id", ""),
                    name = obj.optString("name", ""),
                    conditions = conditions,
                    operator = try {
                        ConditionOperator.valueOf(obj.optString("operator", "AND"))
                    } catch (e: IllegalArgumentException) { ConditionOperator.AND },
                    timeWindowMs = obj.optLong("timeWindowMs", 10_000L),
                    priority = obj.optInt("priority", 5),
                    enabled = obj.optBoolean("enabled", true),
                    reactionPresetId = obj.optString("reactionPresetId", ""),
                    notificationEnabled = obj.optBoolean("notificationEnabled", true),
                    vibrationEnabled = obj.optBoolean("vibrationEnabled", false),
                    soundEnabled = obj.optBoolean("soundEnabled", false),
                    showHeadsUp = obj.optBoolean("showHeadsUp", true),
                    lockScreenPublic = obj.optBoolean("lockScreenPublic", true),
                    minIntervalMinutes = obj.optInt("minIntervalMinutes", 10),
                    conflictStrategy = try {
                        ConflictStrategy.valueOf(obj.optString("conflictStrategy", "HIGHEST_PRIORITY"))
                    } catch (e: IllegalArgumentException) { ConflictStrategy.HIGHEST_PRIORITY }
                )
            )
        }
        return rules
    }

    private fun generateId(): String = UUID.randomUUID().toString()

    companion object {
        private const val TAG = "UserRuleRepository"
        private const val PREFS_NAME = "motionpet_user_rules"
        private const val KEY_RULES = "user_rules_v1"
    }
}
