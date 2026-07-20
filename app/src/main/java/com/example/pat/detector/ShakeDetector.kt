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
    /** 时间窗口内需要达到的波动次数 */
    private val shakeCountRequired: Int = 5,
    /** 统计窗口时长 (ms) */
    private val shakeTimeWindowMs: Long = 1000L
) : MotionDetector<Boolean> {

    private val eventTimes = ArrayDeque<Long>()

    override fun process(data: AccelData): Boolean {
        val magnitude = data.magnitude

        // 计算合加速度偏离重力基线的幅度
        val delta = abs(magnitude - AccelData.GRAVITY)
        if (delta < shakeThreshold) return false

        val now = System.currentTimeMillis()

        // 清理窗口外的过期记录
        while (eventTimes.isNotEmpty() &&
            now - eventTimes.first() > shakeTimeWindowMs
        ) {
            eventTimes.removeFirst()
        }

        // 记录本次波动
        eventTimes.addLast(now)

        // 窗口内记录数达到要求 → 判定为摇晃
        return eventTimes.size >= shakeCountRequired
    }

    override fun reset() {
        eventTimes.clear()
    }
}
