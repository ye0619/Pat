package com.example.pat.monitor

import com.example.pat.event.DeviceEvent
import kotlinx.coroutines.flow.Flow

/**
 * 系统状态监控器接口。
 *
 * 所有具体的 Monitor（Battery / Screen / DeviceState / Sensor）实现此接口，
 * 保证监控层可替换、可单元测试、可在 [EventDispatcher] 中统一管理。
 *
 * 生命周期：
 * ```
 * start() → [运行时持续产生 events] → stop()
 * ```
 *
 * @param T 此 Monitor 产生的事件类型，限定为 [DeviceEvent] 的子类
 */
interface Monitor<T : DeviceEvent> {

    /**
     * 开始监听。
     * 应执行注册 BroadcastReceiver / SensorListener 等操作。
     * 幂等实现：重复调用不应产生副作用。
     */
    fun start()

    /**
     * 停止监听。
     * 应执行注销注册、释放资源等操作。
     * 幂等实现：重复调用不应产生副作用。
     */
    fun stop()

    /**
     * 事件流。
     *
     * 提供此 Monitor 产生的事件序列。
     * 在 [start] 之后开始产生事件，在 [stop] 之后停止。
     *
     * 实现建议：
     * - 使用 [kotlinx.coroutines.flow.callbackFlow] 包装回调/广播
     * - 使用 [kotlinx.coroutines.flow.MutableSharedFlow] 手动控制 emit
     * - 使用 [kotlinx.coroutines.flow.SharedFlow] 作为公开类型
     */
    val events: Flow<T>
}
