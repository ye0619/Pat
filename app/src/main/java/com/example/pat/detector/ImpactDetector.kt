package com.example.pat.detector

import com.example.pat.sensor.AccelData

/**
 * 拍击/撞击检测器。
 *
 * 检测原理：设备从静止状态下受到瞬间高强度加速度冲击（合加速度超过阈值）。
 * 采用滑动窗口 + 峰值检测策略，附带：
 * - 前序静止验证（峰值前窗口内所有样本必须在静止范围内）
 * - 后序回归验证（峰值后 2 帧必须回到静止范围，排除持续运动）
 * - 冷却机制防止连续误触
 *
 * 输入：[AccelData] 加速度计单帧数据
 * 输出：[ImpactResult] 检测结果（Detected / None）
 *
 * v2 改进（减少误触发）：
 * - 阈值 18.0 → 25.0 m/s²（真正的拍击通常 >30，排除放置/拿起手机）
 * - 窗口 3 → 5（更多前序样本）
 * - 静止范围收窄 [9.0, 10.8]（排除手持微动）
 * - 新增后序回归验证：峰值后 2 帧回到静止 → 确认为瞬时冲击
 */
class ImpactDetector(
    /** 撞击判定阈值 (m/s²)，25.0 可区分有意拍击和无意碰撞 */
    private val threshold: Float = 25.0f,
    /** 两次触发之间的最小间隔 (ms)，防止连续误触 */
    private val cooldownMs: Long = 800L,
    /** 滑动窗口大小 */
    private val windowSize: Int = 5
) : MotionDetector<ImpactResult> {

    /** 峰值前静止范围下限 */
    private val stillLower: Float = AccelData.STILL_LOWER_BOUND
    /** 峰值前静止范围上限 */
    private val stillUpper: Float = AccelData.STILL_UPPER_BOUND

    private val window = ArrayDeque<Float>(windowSize)
    /** 峰值后帧计数器：峰值检测到后，等待 N 帧确认回归静止 */
    private var postPeakFrames = 0
    private val postPeakRequired = 2
    /** 候选峰值（等待后序验证） */
    private var candidateIntensity: Float = 0f
    private var lastImpactTime = 0L

    override fun process(data: AccelData): ImpactResult {
        val magnitude = data.magnitude
        val now = System.currentTimeMillis()

        // 冷却检查
        if (now - lastImpactTime < cooldownMs) {
            // 冷却期间仍维护窗口（保持数据连续性），但不检测
            window.addLast(magnitude)
            if (window.size > windowSize) window.removeFirst()
            return ImpactResult.None
        }

        // ── 后序验证阶段：峰值已检测到，等待回归静止 ──
        if (postPeakFrames > 0) {
            postPeakFrames--
            val isBackToStill = magnitude in stillLower..stillUpper

            if (postPeakFrames == 0) {
                // 所有后序帧检查完毕
                if (isBackToStill) {
                    // 回归静止 → 确认为瞬时冲击
                    lastImpactTime = now
                    window.clear()
                    return ImpactResult.Detected(candidateIntensity)
                }
                // 未回归静止 → 持续运动，拒绝
                candidateIntensity = 0f
            } else if (!isBackToStill) {
                // 中间帧未静止 → 提前终止，拒绝候选
                postPeakFrames = 0
                candidateIntensity = 0f
            }

            // 维护窗口
            window.addLast(magnitude)
            if (window.size > windowSize) window.removeFirst()
            return ImpactResult.None
        }

        // ── 正常检测阶段 ──
        // 维护滑动窗口
        window.addLast(magnitude)
        if (window.size > windowSize) window.removeFirst()

        // 前序静止验证：峰值前的所有样本必须在静止范围内
        val previousSamplesCalm = window.size == windowSize &&
                window.dropLast(1).all { it in stillLower..stillUpper }

        // 峰值检测：当前值 > 阈值 且 是窗口内最大值
        val isPeak = magnitude > threshold &&
                (window.size < windowSize || magnitude >= (window.maxOrNull() ?: 0f))

        if (isPeak && previousSamplesCalm) {
            // 候选峰值 → 启动后序回归验证
            candidateIntensity = magnitude
            postPeakFrames = postPeakRequired
        }

        return ImpactResult.None
    }

    override fun reset() {
        window.clear()
        lastImpactTime = 0L
        postPeakFrames = 0
        candidateIntensity = 0f
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
