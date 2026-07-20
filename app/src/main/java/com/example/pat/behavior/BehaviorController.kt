package com.example.pat.behavior

import android.util.Log
import com.example.pat.event.MotionEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 行为控制器。
 *
 * 职责：
 * - 接收 [MotionEvent] 并映射为 [BehaviorState] 转换
 * - 维护当前行为状态快照 [BehaviorSnapshot]
 * - 隔离 detector 层与 UI 层，确保依赖方向正确
 *
 * 输入：[MotionEvent]（通过 [handleEvent] 方法）
 * 输出：[StateFlow]<[BehaviorSnapshot]>（UI 层收集此 Flow 渲染界面）
 *
 * 扩展方向：
 * - 状态机增加更丰富的转换规则
 * - 事件频率控制（debounce、冷却）
 * - 多事件叠加处理（复合事件）
 * - 接入 AudioManager、AnimationController 等反馈模块
 *
 * 参考文档：3.2 behavior/ 模块职责
 */
class BehaviorController {

    private val _snapshot = MutableStateFlow(BehaviorSnapshot())
    val snapshot: StateFlow<BehaviorSnapshot> = _snapshot.asStateFlow()

    /**
     * 处理一个动作事件，更新行为状态。
     *
     * @param event 来自 EventBus 的动作事件
     */
    fun handleEvent(event: MotionEvent) {
        val (newState, eventName, detail) = mapEvent(event)

        _snapshot.value = BehaviorSnapshot(
            state = newState,
            lastEventName = eventName,
            lastEventDetail = detail
        )

        Log.d(TAG, "Event handled: $eventName → $newState")
    }

    /**
     * 将 [MotionEvent] 映射为状态转换 + 显示信息。
     * 独立方法便于后续扩展更复杂的状态机逻辑。
     */
    private fun mapEvent(event: MotionEvent): Triple<BehaviorState, String, String> {
        return when (event) {
            is MotionEvent.Impact -> Triple(
                BehaviorState.HURT,
                "Impact",
                "强度: ${"%.2f".format(event.intensity)}"
            )
            MotionEvent.Shake -> Triple(
                BehaviorState.ANGRY,
                "Shake",
                ""
            )
            is MotionEvent.Drop -> Triple(
                BehaviorState.SCARED,
                "Drop",
                "力度: ${"%.1f".format(event.impactForce)}"
            )
            is MotionEvent.Tilt -> Triple(
                BehaviorState.IDLE,
                "Tilt",
                "pitch=${"%.1f".format(event.pitch)} roll=${"%.1f".format(event.roll)}"
            )
            MotionEvent.Idle -> Triple(
                BehaviorState.IDLE,
                "Idle",
                ""
            )
        }
    }

    companion object {
        private const val TAG = "BehaviorController"
    }
}
