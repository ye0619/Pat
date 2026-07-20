package com.example.pat.detector

import com.example.pat.sensor.AccelData

/**
 * 跌落检测器。
 *
 * 检测原理：设备从静止 → 自由落体（magnitude ≈ 0）→ 撞击的完整过程。
 * 两阶段检测：先确认自由落体持续足够长时间，再检测撞击峰值。
 *
 * 输入：[AccelData] 加速度计单帧数据
 * 输出：[DropResult] 检测结果（Detected / None）
 *
 * 安全约束：
 * - 自由落体持续时间验证（>200ms），防止快速挥动误判
 * - 30 秒内最多触发一次
 *
 * 扩展方向：
 * - 跌落高度估算（自由落体时间 → 高度）
 * - 跌落角度判断（结合陀螺仪）
 *
 * 参考文档：5.3 跌落检测
 */
class DropDetector(
    /** 自由落体判定阈值 (magnitude < 此值视为失重) */
    private val freeFallThreshold: Float = 2.0f,
    /** 自由落体最短持续时间 (ns) */
    private val freeFallDurationNs: Long = 200_000_000L, // 200ms
    /** 撞击判定阈值 (m/s²) */
    private val impactThreshold: Float = 30.0f,
    /** 全局冷却时间 (ms) */
    private val globalCooldownMs: Long = 30_000L // 30秒
) : MotionDetector<DropResult> {

    private enum class Phase { NORMAL, FREE_FALL, IMPACTED }

    private var phase = Phase.NORMAL
    private var freeFallStartTime = 0L
    private var lastDropTime = 0L

    override fun process(data: AccelData): DropResult {
        val magnitude = data.magnitude
        val now = System.currentTimeMillis()

        // 全局冷却检查
        if (now - lastDropTime < globalCooldownMs) {
            return DropResult.None
        }

        return when (phase) {
            Phase.NORMAL -> {
                if (magnitude < freeFallThreshold) {
                    phase = Phase.FREE_FALL
                    freeFallStartTime = data.timestamp
                }
                DropResult.None
            }

            Phase.FREE_FALL -> {
                val elapsed = data.timestamp - freeFallStartTime

                if (magnitude >= freeFallThreshold && elapsed >= freeFallDurationNs) {
                    // 自由落体结束，准备检测撞击
                    phase = Phase.IMPACTED
                    DropResult.None
                } else if (magnitude >= freeFallThreshold) {
                    // 自由落体时间太短 → 误触，重置
                    phase = Phase.NORMAL
                    DropResult.None
                } else {
                    DropResult.None // 仍在自由落体中
                }
            }

            Phase.IMPACTED -> {
                phase = Phase.NORMAL // 无论结果如何，重置状态
                if (magnitude > impactThreshold) {
                    lastDropTime = now
                    DropResult.Detected(magnitude)
                } else {
                    DropResult.None
                }
            }
        }
    }

    override fun reset() {
        phase = Phase.NORMAL
        freeFallStartTime = 0L
    }
}

/**
 * 跌落检测结果。
 *
 * @property impactForce 撞击峰值力度 (m/s²)
 */
sealed class DropResult {
    data class Detected(val impactForce: Float) : DropResult()
    data object None : DropResult()
}
