package com.example.pat.detector

import com.example.pat.sensor.AccelData
import kotlin.math.abs

/**
 * 摇晃检测器。
 *
 * 检测原理：设备在短时间内经历连续、大幅度的加速度方向变化。
 * 通过记录合加速度偏离重力基线的次数，在时间窗口内超过阈值即判定为摇晃。
 *
 * 输入：[AccelData] 加速度计单帧数据
 * 输出：Boolean（true = 检测到摇晃）
 *
 * 扩展方向：
 * - 摇晃强度量化（剧烈 / 温和）
 * - 摇晃频率分析（快摇 / 慢摇）
 * - 多轴独立分析（左右晃 vs 上下晃）
 *
 * 参考文档：5.2 摇晃检测
 */
class ShakeDetector(
    /** 单次波动偏离重力的阈值 (m/s²) */
    private val shakeThreshold: Float = 13.0f,
    /** 时间窗口内需要达到的波动次数（提高要求以减少走路/跑步误触发） */
    private val shakeCountRequired: Int = 7,
    /** 统计窗口时长 (ms)（缩短窗口要求更高摇晃频率） */
    private val shakeTimeWindowMs: Long = 700L,
    /** 两次摇晃触发之间的最小间隔 (ms)，防止持续摇晃时重复触发 */
    private val shakeCooldownMs: Long = 2000L
) : MotionDetector<Boolean> {

    private val eventTimes = ArrayDeque<Long>()
    /** 上次摇晃触发时间（挂钟毫秒），用于冷却判断 */
    private var lastShakeTime = 0L

    override fun process(data: AccelData): Boolean {
        val magnitude = data.magnitude
        val now = System.currentTimeMillis()

        // 冷却检查：触发后一段时间内忽略所有输入
        if (now - lastShakeTime < shakeCooldownMs) return false

        // 计算合加速度偏离重力基线的幅度
        val delta = abs(magnitude - AccelData.GRAVITY)
        if (delta < shakeThreshold) return false

        // 清理窗口外的过期记录
        while (eventTimes.isNotEmpty() &&
            now - eventTimes.first() > shakeTimeWindowMs
        ) {
            eventTimes.removeFirst()
        }

        // 记录本次波动
        eventTimes.addLast(now)

        // 窗口内记录数达到要求 → 判定为摇晃
        val detected = eventTimes.size >= shakeCountRequired
        if (detected) {
            // 触发后清空窗口并进入冷却，防止冷却结束后残留数据导致立即再次触发
            lastShakeTime = now
            eventTimes.clear()
        }
        return detected
    }

    override fun reset() {
        eventTimes.clear()
        lastShakeTime = 0L
    }
}
