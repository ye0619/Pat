package com.example.pat.engine

import com.example.pat.event.AtomicEventType
import com.example.pat.model.EventDefinition

/**
 * 冲突检测器 —— 检测不同事件定义之间的条件冲突。
 * 不同事件使用了相同的原子条件类型则为冲突，无法同时启动。
 */
object ConflictDetector {

    fun detect(defs: List<EventDefinition>): Map<String, List<String>> {
        val enabled = defs.filter { it.enabled }
        val conflicts = mutableMapOf<String, MutableList<String>>()
        for (i in enabled.indices) for (j in i + 1 until enabled.size) {
            val ta = usedTypes(enabled[i]); val tb = usedTypes(enabled[j])
            if (ta.intersect(tb).isNotEmpty()) {
                conflicts.getOrPut(enabled[i].id) { mutableListOf() }.add(enabled[j].id)
                conflicts.getOrPut(enabled[j].id) { mutableListOf() }.add(enabled[i].id)
            }
        }
        return conflicts
    }

    fun conflictingIds(defs: List<EventDefinition>): Set<String> = detect(defs).keys

    fun hasConflict(def: EventDefinition, others: List<EventDefinition>): Boolean {
        val types = usedTypes(def)
        return others.any { it.id != def.id && it.enabled && usedTypes(it).intersect(types).isNotEmpty() }
    }

    private fun usedTypes(def: EventDefinition): Set<AtomicEventType> =
        def.conditionGroups.flatMap { it.conditions }.map { it.atomicType }.toSet()
}
