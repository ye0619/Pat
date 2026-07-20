package com.example.pat.monitor

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.util.Log
import com.example.pat.event.DeviceEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.Calendar

/**
 * 屏幕状态监控器。
 *
 * 监听屏幕亮灭和用户解锁事件，产生：
 * - [DeviceEvent.ScreenWake]：点亮屏幕或解锁
 * - [DeviceEvent.LateNight]：深夜使用（23:00–05:00），每次 SCREEN_WAKE 时检测
 *
 * 同时跟踪累计亮屏时长，通过 [checkLongUsage] 方法供外部查询。
 *
 * 实现要点：
 * - ACTION_SCREEN_ON 在 API 26+ 上无法通过 Manifest 注册，必须动态注册
 * - 使用 [SystemClock.elapsedRealtime] 累计亮屏时长（单调时钟，不受系统时间调整影响）
 *
 * 参考文档：5.4 屏幕监控设计
 */
class ScreenMonitor(
    private val context: Context
) : Monitor<DeviceEvent> {

    private val _events = MutableSharedFlow<DeviceEvent>(
        replay = 0,
        extraBufferCapacity = 8
    )
    override val events: SharedFlow<DeviceEvent> = _events.asSharedFlow()

    private var isStarted = false
    private var receiver: BroadcastReceiver? = null

    /**
     * 累计亮屏时长跟踪。
     * sessionStartTime: 当前亮屏段开始时间（elapsedRealtime），0 表示灭屏
     * totalAccumulatedMs: 之前所有亮屏段的累计时长
     * longUsageEmitted: 本次 session 是否已经发射过 LONG_USAGE
     */
    private var sessionStartTime: Long = 0L
    private var totalAccumulatedMs: Long = 0L
    private var longUsageEmitted: Boolean = false

    /** 深夜发射标记：同一段深夜范围只发射一次 */
    private var lateNightEmitted: Boolean = false

    override fun start() {
        if (isStarted) return
        isStarted = true
        sessionStartTime = 0L
        lateNightEmitted = false

        val screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        // 开始累计这一段的亮屏时长
                        sessionStartTime = SystemClock.elapsedRealtime()

                        // 深夜检测
                        if (!lateNightEmitted && isLateNightHour()) {
                            lateNightEmitted = true
                            _events.tryEmit(DeviceEvent.LateNight)
                            Log.d(TAG, "Event: LateNight")
                        }

                        _events.tryEmit(DeviceEvent.ScreenWake)
                        Log.d(TAG, "Event: ScreenWake")
                    }

                    Intent.ACTION_SCREEN_OFF -> {
                        // 停止累计：将当前段的时长加入总累计
                        if (sessionStartTime > 0L) {
                            totalAccumulatedMs +=
                                SystemClock.elapsedRealtime() - sessionStartTime
                            sessionStartTime = 0L
                        }
                    }

                    Intent.ACTION_USER_PRESENT -> {
                        // 解锁事件：某些设备上 SCREEN_ON 比 USER_PRESENT 早
                        // 如果还没有开始累计（例如从锁屏唤醒），立即开始
                        if (sessionStartTime == 0L) {
                            sessionStartTime = SystemClock.elapsedRealtime()
                        }
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        context.registerReceiver(screenReceiver, filter)
        receiver = screenReceiver
        Log.i(TAG, "ScreenMonitor started")
    }

    override fun stop() {
        if (!isStarted) return
        isStarted = false
        receiver?.let { context.unregisterReceiver(it) }
        receiver = null
        lateNightEmitted = false
        longUsageEmitted = false
        sessionStartTime = 0L
        totalAccumulatedMs = 0L
        Log.i(TAG, "ScreenMonitor stopped")
    }

    /**
     * 计算当前累计亮屏时长并检查是否超过阈值。
     * 超过阈值则发射 [DeviceEvent.LongUsage]（仅一次）。
     *
     * @param thresholdMinutes 阈值分钟数（默认 120 分钟）
     * @return 是否发射了 LongUsage
     */
    fun checkLongUsage(thresholdMinutes: Int = 120): Boolean {
        if (longUsageEmitted) return false

        val totalMs = if (sessionStartTime > 0L) {
            totalAccumulatedMs + (SystemClock.elapsedRealtime() - sessionStartTime)
        } else {
            totalAccumulatedMs
        }
        val totalMinutes = (totalMs / 60_000).toInt()

        if (totalMinutes >= thresholdMinutes) {
            longUsageEmitted = true
            _events.tryEmit(DeviceEvent.LongUsage(minutes = totalMinutes))
            Log.d(TAG, "Event: LongUsage (${totalMinutes}min)")
            return true
        }
        return false
    }

    /**
     * 检查当前时间是否为深夜时段（23:00–04:59）。
     */
    private fun isLateNightHour(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour >= 23 || hour < 5
    }

    companion object {
        private const val TAG = "ScreenMonitor"
    }
}
