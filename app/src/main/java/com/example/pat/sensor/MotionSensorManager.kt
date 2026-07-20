package com.example.pat.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 传感器生命周期管理器。
 *
 * 职责：
 * - 封装 Android [SensorManager] 的注册/注销生命周期
 * - 通过 [startListening] / [stopListening] 显式控制传感器启停
 * - 通过 [sensorData] SharedFlow 提供响应式数据流
 * - 通过 [SensorCallback] 提供基于监听的接入方式
 *
 * 输入：Context（用于获取 SensorManager 系统服务）
 * 输出：[SharedFlow]<[AccelData]> 加速度计数据流 + [SensorCallback] 回调
 *
 * 生命周期安全：
 * - [startListening] 防止重复注册
 * - [stopListening] 安全注销，防止内存泄漏
 * - [release] 完整清理，应在 Activity/Fragment onDestroy 中调用
 * - Flow 和 Callback 双通道互不干扰
 *
 * 参考文档：5.2 传感器监控设计
 */
class MotionSensorManager(context: Context) {

    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    /** 当前最新的传感器数据，非协程消费者可直接读取此属性。 */
    @Volatile
    var latestData: AccelData = AccelData(0f, 0f, 0f, 0L)
        private set

    private var _isListening = false

    /** 传感器当前是否在监听中。 */
    val isRunning: Boolean get() = _isListening

    private val callbacks = mutableListOf<SensorCallback>()

    private val _sensorData = MutableSharedFlow<AccelData>(
        replay = 0,
        extraBufferCapacity = 64 // 传感器高频数据，buffer 需足够
    )

    /**
     * 加速度计数据流。
     *
     * 适用于协程消费者，通过 [SharedFlow] 提供无粘性实时数据。
     * 不与 [startListening]/[stopListening] 绑定，
     * 即使没有收集者，传感器数据依然会 emit（当监听已启动时）。
     */
    val sensorData: SharedFlow<AccelData> = _sensorData.asSharedFlow()

    /**
     * 传感器监听器实例。
     * 持有引用以确保 [unregisterListener] 能正确匹配。
     */
    private val sensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val data = AccelData(
                x = event.values[0],
                y = event.values[1],
                z = event.values[2],
                timestamp = event.timestamp
            )

            // 更新最新值缓存（非协程消费者可直接读取）
            latestData = data

            // 发送到 Flow 通道
            _sensorData.tryEmit(data)

            // 通知回调消费者
            synchronized(callbacks) {
                callbacks.forEach { it.onSensorChanged(data) }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
            synchronized(callbacks) {
                callbacks.forEach { it.onAccuracyChanged(sensor.type, accuracy) }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 生命周期控制
    // ══════════════════════════════════════════════════════════════

    /**
     * 开始监听加速度计。
     *
     * 安全约束：
     * - 防重复注册：如果已在监听中，直接返回
     * - 设备兼容：如果设备无加速度计，记录错误并返回
     * - 与 [stopListening] 配对使用
     */
    fun startListening() {
        if (_isListening) {
            Log.w(TAG, "startListening() ignored — already listening")
            return
        }

        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensor == null) {
            Log.e(TAG, "Cannot start: Accelerometer not available on this device")
            return
        }

        sensorManager.registerListener(
            sensorEventListener,
            sensor,
            SensorManager.SENSOR_DELAY_GAME // ~20ms，确保检测流畅
        )

        _isListening = true
        Log.i(TAG, "Accelerometer listener registered (SENSOR_DELAY_GAME)")
    }

    /**
     * 停止监听加速度计。
     *
     * 安全约束：
     * - 防重复注销：如果未在监听中，直接返回
     * - 自动注销所有已注册的传感器监听器
     */
    fun stopListening() {
        if (!_isListening) {
            Log.w(TAG, "stopListening() ignored — not listening")
            return
        }

        sensorManager.unregisterListener(sensorEventListener)
        _isListening = false
        Log.i(TAG, "Accelerometer listener unregistered")
    }

    // ══════════════════════════════════════════════════════════════
    // 回调管理
    // ══════════════════════════════════════════════════════════════

    /**
     * 注册传感器数据回调。
     * 回调在传感器事件线程中调用，不应执行耗时操作。
     * 防重复注册：同实例不会重复添加。
     */
    fun registerCallback(callback: SensorCallback) {
        synchronized(callbacks) {
            if (!callbacks.contains(callback)) {
                callbacks.add(callback)
            }
        }
    }

    /**
     * 注销传感器数据回调。
     */
    fun unregisterCallback(callback: SensorCallback) {
        synchronized(callbacks) {
            callbacks.remove(callback)
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 资源释放
    // ══════════════════════════════════════════════════════════════

    /**
     * 释放所有传感器资源。
     *
     * 执行以下操作：
     * 1. 停止传感器监听（如果正在运行）
     * 2. 清空所有回调引用（防止泄漏）
     *
     * 应在 [android.app.Activity.onDestroy] 中调用。
     * 调用后此实例不再可用，需重新创建。
     */
    fun release() {
        stopListening()
        synchronized(callbacks) {
            callbacks.clear()
        }
        Log.i(TAG, "MotionSensorManager released")
    }

    companion object {
        private const val TAG = "MotionSensorManager"
    }
}
