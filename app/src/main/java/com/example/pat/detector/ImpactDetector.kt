package com.example.pat.detector

import com.example.pat.sensor.AccelData

/**
 * 拍击/撞击检测器。
 *
 * 检测原理：设备从静止状态下受到瞬间高强度加速度冲击（合加速度超过阈值）。
 * 采用滑动窗口 + 峰值检测策略，附带冷却机制防止连续误触。
 *
 * 输入：[AccelData] 加速度计单帧数据
 * 输出：[ImpactResult] 检测结果（Detected / None）
 *
 * 扩展方向：
 * - 自适应阈值：根据设备静止噪声动态计算阈值
 * - 强度归一化：将原始 magnitude 映射到 [0, 1] 区间
 * - 多级拍击分类（轻拍 / 重拍）
 *
 * 参考文档：5.1 拍击检测
 */
class ImpactDetector(
    /** 撞击判定阈值 (m/s²)，超过此值视为潜在撞击 */
    private val threshold: Float = 25.0f,
    /** 两次触发之间的最小间隔 (ms)，防止连续误触 */
    private val cooldownMs: Long = 300L,
    /** 滑动窗口大小 */
    private val windowSize: Int = 5
) : MotionDetector<ImpactResult> {

    private val window = ArrayDeque<Float>(windowSize)
    private var lastImpactTime = 0L

    override fun process(data: AccelData): ImpactResult {
        val magnitude = data.magnitude
        val now = System.currentTimeMillis()

        // 维护滑动窗口
        window.addLast(magnitude)
        if (window.size > windowSize) window.removeFirst()

        // 冷却检查
        if (now - lastImpactTime < cooldownMs) {
            return ImpactResult.None
        }

        // 峰值检测：当前值 > 阈值 且 是窗口内最大值
        val isCalm = window.size == windowSize &&
                window.all { it in AccelData.STILL_LOWER_BOUND..AccelData.STILL_UPPER_BOUND }

        val isPeak = magnitude > threshold &&
                (window.size < windowSize || magnitude == window.maxOrNull())

        if (isPeak && isCalm) {
            lastImpactTime = now
            return ImpactResult.Detected(magnitude)
        }

        return ImpactResult.None
    }

    override fun reset() {
        window.clear()
        lastImpactTime = 0L
    }
}

/**
 * 撞击检测结果。
 *
 * @property intensity 原始加速度峰值 (m/s²)
 */
sealed class ImpactResult {
    data class Detected(val intensity: Float) : ImpactResult()
    data object None : ImpactResult()
}
