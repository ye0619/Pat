package com.example.pat.preset

import android.content.Context
import android.util.Log
import com.example.pat.event.EventType
import java.util.UUID

/**
 * 预设加载器 —— 从 assets/ 中扫描并解析预设音频文件。
 *
 * 职责：
 * - 通过 AssetManager 列出 assets/ 中所有 .wav 文件
 * - 解析文件名格式：事件类型（文本内容）.wav
 * - 使用事件名称映射表将中文事件名转换为 [EventType]
 * - 生成 [PresetReaction] 列表
 *
 * 文件名解析规则：
 * ```
 * 输入：长时间使用（别看了，我想睡觉了）.wav
 * 解析：eventName="长时间使用", displayText="别看了，我想睡觉了"
 * 映射：eventName → EventType.SCREEN_LONG_USAGE
 * ```
 *
 * 事件名称映射表（唯一硬编码部分）：
 * - 摇晃手机 / 晃动 → SHAKE
 * - 撞击 → IMPACT
 * - 长时间使用 → SCREEN_LONG_USAGE
 * - 充电 → CHARGE_START
 * - 低电量 → LOW_BATTERY
 *
 * 参考：Task 2, 3, 4 - 资源映射、文件名解析、默认预设加载
 */
class PresetLoader(
    private val context: Context
) {
    /**
     * 事件名称 → EventType 映射表。
     *
     * 这是唯一需要维护的映射关系。
     * 新增事件类型时，只需在此表中添加对应的中文名称即可。
     */
    private val eventNameMap: Map<String, EventType> = mapOf(
        "摇晃手机" to EventType.SHAKE,
        "晃动" to EventType.SHAKE,
        "撞击" to EventType.IMPACT,
        "长时间使用" to EventType.SCREEN_LONG_USAGE,
        "充电" to EventType.CHARGE_START,
        "低电量" to EventType.LOW_BATTERY
    )

    /**
     * 文件名解析正则。
     *
     * 匹配格式：事件名称（文本内容）.wav
     * - group 1: 事件名称（如 "长时间使用"）
     * - group 2: 文本内容（如 "别看了，我想睡觉了"）
     *
     * 注意：文件名可能包含扩展名（.wav），解析时会自动去除。
     */
    private val fileNamePattern = Regex("""^(.+?)（(.+?)）(?:\.\w+)?$""")

    /**
     * 加载所有内置预设。
     *
     * 通过 AssetManager 扫描 assets/ 目录，
     * 解析每个 .wav 文件名为 [PresetReaction]。
     * 无法解析的文件将被跳过并记录日志。
     *
     * @return 成功解析的预设列表
     */
    fun load(): List<PresetReaction> {
        val presets = mutableListOf<PresetReaction>()

        try {
            val assetFiles = context.assets.list("") ?: emptyArray()

            for (fileName in assetFiles) {
                // 仅处理 .wav 文件
                if (!fileName.endsWith(".wav", ignoreCase = true)) continue

                // 去除扩展名进行解析
                val nameWithoutExt = fileName.removeSuffix(".wav").removeSuffix(".WAV")

                val parsed = parseFileName(nameWithoutExt)
                if (parsed != null) {
                    val (eventName, displayText) = parsed
                    val eventType = eventNameMap[eventName]

                    if (eventType != null) {
                        presets.add(
                            PresetReaction(
                                id = UUID.randomUUID().toString(),
                                eventType = eventType,
                                displayText = displayText,
                                audioAssetPath = fileName
                            )
                        )
                        Log.d(TAG, "Loaded preset: [$eventType] \"$displayText\" → $fileName")
                    } else {
                        Log.w(TAG, "Unknown event name \"$eventName\" in file: $fileName")
                    }
                } else {
                    // 非预设文件，跳过
                    Log.v(TAG, "Skipping non-preset asset: $fileName")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to scan assets for presets", e)
        }

        Log.i(TAG, "Loaded ${presets.size} presets for ${presets.map { it.eventType }.distinct().size} event types")
        return presets
    }

    /**
     * 解析文件名，提取事件名称和显示文本。
     *
     * 格式：事件名称（文本内容）
     *
     * 示例：
     * - "长时间使用（别看了，我想睡觉了）" → ("长时间使用", "别看了，我想睡觉了")
     * - "充电（啊，太好了）" → ("充电", "啊，太好了")
     * - "not_a_preset_file" → null
     *
     * @param fileName 文件名（不含扩展名）
     * @return Pair(事件名称, 显示文本)，无法解析时返回 null
     */
    fun parseFileName(fileName: String): Pair<String, String>? {
        val match = fileNamePattern.find(fileName) ?: return null
        val eventName = match.groupValues[1]
        val displayText = match.groupValues[2]
        return Pair(eventName, displayText)
    }

    companion object {
        private const val TAG = "PresetLoader"
    }
}
