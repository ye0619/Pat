package com.example.pat.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.pat.event.AtomicEvent
import com.example.pat.event.AtomicEventBus

/**
 * 点击检测无障碍服务（默认关闭）。
 *
 * 检测屏幕触摸交互事件，向 [AtomicEventBus] 发射 [AtomicEvent.Click]。
 *
 * v2 改进：
 * - 从仅监听 TYPE_VIEW_CLICKED 扩大到 TYPE_VIEW_CLICKED + TYPE_TOUCH_INTERACTION_START
 * - TYPE_TOUCH_INTERACTION_START 捕获所有触摸开始事件（包括桌面、空白区域）
 * - 内置 150ms 去抖，防止高频重复发射
 * - 增加 flagReportViewIds 以获取触发事件的 View ID
 *
 * 注意：
 * - 此服务需要用户在系统设置中手动开启（设置 → 无障碍 → Pat）
 * - 开启后会影响系统性能（每次触摸都触发回调）
 * - Google Play 可能审查无障碍服务用途，建议仅在需要时引导用户开启
 *
 * 替代方案（未来考虑）：
 * - 使用 WindowManager Overlay 捕获触摸（无需无障碍权限）
 * - 使用 AccessibilityService 的 Gesture 检测（TYPE_GESTURE_DETECTED）
 */
class ClickAccessibilityService : AccessibilityService() {

    /** 上次发射 Click 事件的时间戳（去抖） */
    private var lastClickEmitted: Long = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val now = System.currentTimeMillis()

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                Log.d(TAG, "TYPE_VIEW_CLICKED: package=${event.packageName} class=${event.className}")
                emitClick(now)
            }
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_START -> {
                Log.d(TAG, "TYPE_TOUCH_INTERACTION_START: package=${event.packageName}")
                emitClick(now)
            }
            else -> {
                Log.v(TAG, "Other event type: ${event.eventType}")
            }
        }
    }

    /**
     * 去抖后发射 Click 原子事件。
     * 150ms 内相同类型的触摸仅发射一次。
     */
    private fun emitClick(now: Long) {
        if (now - lastClickEmitted < DEBOUNCE_MS) {
            Log.v(TAG, "Click debounced (${now - lastClickEmitted}ms since last)")
            return
        }
        lastClickEmitted = now
        val emitted = AtomicEventBus.tryEmit(AtomicEvent.Click(now))
        if (emitted) {
            Log.i(TAG, "Click event emitted at $now")
        } else {
            Log.w(TAG, "Click event dropped (buffer full)")
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Click accessibility service connected — " +
                "listening for TYPE_VIEW_CLICKED + TYPE_TOUCH_INTERACTION_START")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Click accessibility service destroyed")
    }

    companion object {
        private const val TAG = "ClickAccessibility"

        /** 去抖间隔（毫秒） */
        private const val DEBOUNCE_MS = 150L
    }
}
