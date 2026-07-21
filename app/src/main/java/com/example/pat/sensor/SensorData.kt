package com.example.pat.sensor

import kotlin.math.sqrt

/**
 * 加速度计原始数据。
 *
 * 封装 Android [android.hardware.SensorEvent] 中加速度计的三个轴数据及时间戳。
 * 提供合加速度 [magnitude] 计算，供 detector 层进行模式识别。
 *
 * @property x X 轴加速度 (m/s²)，设备左右方向
 * @property y Y 轴加速度 (m/s²)，设备上下方向
 * @property z Z 轴加速度 (m/s²)，垂直于屏幕方向
 * @property timestamp 事件发生时间戳（纳秒，来自 SensorEvent.timestamp）
 */
data class AccelData(
    val x: Float,
    val y: Float,
    val z: Float,
    val timestamp: Long
) {
    /** 合加速度 magnitude = sqrt(x² + y² + z²) */
    val magnitude: Float get() = sqrt(x * x + y * y + z * z)

    companion object {
        /** 重力加速度参考值 (m/s²) */
        const val GRAVITY: Float = 9.80665f

        /**
         * 静止状态下 magnitude 的合理下限。
         * 手持手机时微动会偏离纯重力值，7.5 覆盖正常手持场景。
         */
        const val STILL_LOWER_BOUND: Float = 7.5f

        /**
         * 静止状态下 magnitude 的合理上限。
         * 12.5 ≈ 1.27g，排除故意摇晃但允许手持微动。
         */
        const val STILL_UPPER_BOUND: Float = 12.5f
    }
}

/**
 * 陀螺仪原始数据。
 *
 * 扩展预留：当前阶段仅使用加速度计，陀螺仪数据类在此定义以供后续扩展。
 *
 * @property rotX 绕 X 轴旋转角速度 (rad/s) —— Pitch
 * @property rotY 绕 Y 轴旋转角速度 (rad/s) —— Roll
 * @property rotZ 绕 Z 轴旋转角速度 (rad/s) —— Yaw
 * @property timestamp 事件发生时间戳（纳秒）
 */
data class GyroData(
    val rotX: Float,
    val rotY: Float,
    val rotZ: Float,
    val timestamp: Long
)
