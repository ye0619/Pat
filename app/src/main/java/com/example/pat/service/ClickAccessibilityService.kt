package com.example.pat.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.pat.event.AtomicEvent
import com.example.pat.event.AtomicEventBus

/**
 * 点击检测无障碍服务（默认关闭，⚠️ 不推荐开启）。
 *
 * 检测屏幕点击事件（TYPE_VIEW_CLICKED），向 [AtomicEventBus] 发射 [AtomicEvent.Click]。
 *
 * 注意：
 * - 此服务需要用户在系统设置中手动开启
 * - 开启后会影响系统性能（每次点击都触发回调）
 * - 仅在用户明确需要"点击"条件时才建议开启
 *
 * 开启方式：设置 → 无障碍 → Pat → 开启
 */
class ClickAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val now = System.currentTimeMillis()
            AtomicEventBus.tryEmit(AtomicEvent.Click(now))
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Click accessibility service connected")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Click accessibility service destroyed")
    }

    companion object {
        private const val TAG = "ClickAccessibility"
    }
}
