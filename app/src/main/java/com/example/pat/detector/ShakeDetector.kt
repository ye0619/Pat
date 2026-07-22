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
    private var shakeThresholdMin: Float = 10.0f,
    private var shakeThresholdMax: Float = 30.0f,
    private var shakeCountRequired: Int = 5,
    private var shakeTimeWindowMs: Long = 1000L,
    private val shakeCooldownMs: Long = 2000L
) : MotionDetector<Boolean> {

    fun updateParams(amin: Float, amax: Float, n: Int, t: Long) {
        shakeThresholdMin = amin; shakeThresholdMax = amax
        shakeCountRequired = n; shakeTimeWindowMs = t
    }

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
        // 只计数在 (amin, amax) 区间内的加速度波动
        if (delta < shakeThresholdMin || delta > shakeThresholdMax) return false

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
