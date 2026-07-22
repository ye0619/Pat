package com.example.pat.preset

import android.content.Context
import android.util.Log
import com.example.pat.event.EventType
import com.example.pat.model.AudioType
import com.example.pat.model.ReactionPreset

/**
 * 预设加载器 —— 从 assets/ 中扫描并解析内置预设音频文件。
 *
 * 职责：
 * - 通过 AssetManager 列出 assets/ 中所有 .wav 文件
 * - 解析文件名格式：事件类型（文本内容）.wav
 * - 使用事件名称映射表将中文事件名转换为 [EventType]
 * - 生成 [ReactionPreset] 列表（均为 AudioType.PRESET）
 *
 * 文件名解析规则：
 * ```
 * 输入：长时间使用（别看了，我想睡觉了）.wav
 * 解析：eventName="长时间使用", displayText="别看了，我想睡觉了"
 * 映射：eventName → EventType.SCREEN_LONG_USAGE
 * 输出：ReactionPreset(id=..., name="别看了，我想睡觉了", text="别看了，我想睡觉了", ...)
 * ```
 */
class PresetLoader(
    private val context: Context
) {
    private val eventNameMap: Map<String, EventType> = mapOf(
        "摇晃手机" to EventType.SHAKE,
        "晃动" to EventType.SHAKE,
        "坠落" to EventType.DROP,
        "撞击" to EventType.DROP,  // 旧名称兼容
        "长时间使用" to EventType.SCREEN_LONG_USAGE,
        "充电" to EventType.CHARGE_START,
        "低电量" to EventType.LOW_BATTERY
    )

    private val fileNamePattern = Regex("""^(.+?)（(.+?)）(?:\.\w+)?$""")

    /**
     * 加载所有内置预设，返回 [ReactionPreset] 列表。
     */
    fun load(): List<ReactionPreset> {
        val presets = mutableListOf<ReactionPreset>()

        try {
            val assetFiles = context.assets.list("") ?: emptyArray()

            for (fileName in assetFiles) {
                if (!fileName.endsWith(".wav", ignoreCase = true)) continue

                val nameWithoutExt = fileName.removeSuffix(".wav").removeSuffix(".WAV")
                val parsed = parseFileName(nameWithoutExt)

                if (parsed != null) {
                    val (eventName, displayText) = parsed
                    val eventType = eventNameMap[eventName]

                    if (eventType != null) {
                        presets.add(
                            ReactionPreset(
                                id = buildPresetId(eventType, fileName),
                                name = ReactionPreset.nameFromText(displayText),
                                text = displayText,
                                audioAssetPath = fileName,
                                audioType = AudioType.PRESET,
                                eventType = eventType
                            )
                        )
                        Log.d(TAG, "Loaded built-in preset: [$eventType] \"$displayText\" → $fileName")
                    } else {
                        Log.w(TAG, "Unknown event name \"$eventName\" in file: $fileName")
                    }
                } else {
                    Log.v(TAG, "Skipping non-preset asset: $fileName")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to scan assets for presets", e)
        }

        Log.i(TAG, "Loaded ${presets.size} built-in presets for ${presets.map { it.eventType }.distinct().size} event types")
        return presets
    }

    /**
     * 解析文件名，提取事件名称和显示文本。
     */
    fun parseFileName(fileName: String): Pair<String, String>? {
        val match = fileNamePattern.find(fileName) ?: return null
        val eventName = match.groupValues[1]
        val displayText = match.groupValues[2]
        return Pair(eventName, displayText)
    }

    /**
     * 生成稳定的预设 ID —— 基于事件类型和文件名哈希。
     * 同一音频文件在任何时候加载都会得到相同的 ID。
     */
    private fun buildPresetId(eventType: EventType, fileName: String): String {
        return "builtin_${eventType.name}_${fileName.hashCode().toUInt()}"
    }

    companion object {
        private const val TAG = "PresetLoader"
    }
}
