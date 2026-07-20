package com.example.pat.sensor

/**
 * 传感器数据回调接口。
 *
 * 为不需要 Flow 订阅的消费者提供基于监听的接入方式。
 * 与 [MotionSensorManager] 的 register/unregister 配合使用。
 *
 * 使用场景：
 * - 非协程上下文中的传感器数据消费
 * - 需要实时响应传感器事件的遗留组件
 * - UI 层直接获取最新传感器值（通过 latestData + Flow 组合方案更推荐）
 *
 * 参考文档：5.1 Monitor 抽象设计
 */
interface SensorCallback {

    /**
     * 传感器数据更新回调。
     * 在传感器事件线程中调用，不应在此方法中执行耗时操作。
     *
     * @param data 当前帧加速度数据
     */
    fun onSensorChanged(data: AccelData)

    /**
     * 传感器精度变化回调。
     *
     * @param sensorType 传感器类型（Sensor.TYPE_ACCELEROMETER 等）
     * @param accuracy 新精度等级（SENSOR_STATUS_ACCURACY_*）
     */
    fun onAccuracyChanged(sensorType: Int, accuracy: Int)
}
