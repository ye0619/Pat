package com.example.pat.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers

/**
 * 传感器生命周期管理器。
 *
 * 职责：
 * - 封装 Android [SensorManager] 的注册/注销生命周期
 * - 通过 [callbackFlow] 将传感器回调转换为响应式 [Flow]
 * - 提供加速度计数据流供 detector 层订阅
 *
 * 输入：Context（用于获取 SensorManager 系统服务）
 * 输出：[Flow]<[AccelData]> 加速度计数据流
 *
 * 扩展方向：
 * - 添加陀螺仪数据流 [gyroFlow]
 * - 动态采样率切换（高帧率检测 / 低帧率待机）
 * - 多传感器融合（Sensor Fusion）
 *
 * 生命周期安全：
 * - Flow 在收集端取消时自动注销 SensorEventListener
 * - 建议配合 [androidx.lifecycle.repeatOnLifecycle] 使用
 */
class MotionSensorManager(context: Context) {

    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    /** 加速度计数据流。停止收集时自动注销监听器。 */
    val accelerometerFlow: Flow<AccelData> = callbackFlow {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensor == null) {
            Log.w(TAG, "Accelerometer not available on this device")
            close() // 无传感器时直接关闭 Flow
            return@callbackFlow
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val (x, y, z) = event.values
                val data = AccelData(x, y, z, event.timestamp)
                trySend(data)

                // TODO: 阶段1验证后移除或降级详细 Log
                Log.v(TAG, "Accel: x=%.2f y=%.2f z=%.2f mag=%.2f"
                    .format(x, y, z, data.magnitude))
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                // 精度变化时记录日志，便于排查设备差异
                Log.d(TAG, "Accuracy changed: sensor=${sensor.type} accuracy=$accuracy")
            }
        }

        // 使用 GAME 精度 (~20ms) 确保检测流畅
        sensorManager.registerListener(
            listener,
            sensor,
            SensorManager.SENSOR_DELAY_GAME
        )

        Log.i(TAG, "Accelerometer listener registered (SENSOR_DELAY_GAME)")

        awaitClose {
            sensorManager.unregisterListener(listener)
            Log.i(TAG, "Accelerometer listener unregistered")
        }
    }.flowOn(Dispatchers.Default) // 传感器数据在 Default 调度器处理，避免阻塞主线程

    /**
     * 释放所有传感器资源。
     * 当 Flow 收集被取消时自动触发，此方法作为兜底释放入口。
     */
    fun release() {
        // 注销所有监听器：传入(null as SensorEventListener) 解决重载歧义
        sensorManager.unregisterListener(null as SensorEventListener?)
        Log.i(TAG, "All sensor listeners released")
    }

    companion object {
        private const val TAG = "MotionSensorManager"
    }
}
