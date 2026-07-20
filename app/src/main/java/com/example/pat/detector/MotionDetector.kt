package com.example.pat.detector

import com.example.pat.sensor.AccelData

/**
 * 行为检测器接口。
 *
 * 所有具体检测器（Impact、Shake、Drop 等）实现此接口，
 * 保证 detector 层可替换、可单元测试。
 *
 * @param T 检测结果类型，由子类定义（如 ImpactResult、Boolean 等）
 */
interface MotionDetector<T> {

    /**
     * 处理一帧加速度数据。
     *
     * @param data 当前帧加速度数据
     * @return 检测结果，类型由具体实现定义
     */
    fun process(data: AccelData): T

    /**
     * 重置检测器内部状态。
     * 在传感器重新注册或检测场景切换时调用。
     */
    fun reset()
}
