package com.example.pat.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.example.pat.event.AtomicEvent
import com.example.pat.event.AtomicEventBus
import com.example.pat.event.DeviceEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 屏幕状态监控器。
 *
 * 监听屏幕亮灭和用户解锁事件，产生：
 * - [DeviceEvent.ScreenWake]：屏幕点亮
 * - [DeviceEvent.ScreenOff]：屏幕关闭
 * - [DeviceEvent.LateNight]：深夜使用（23:00–05:00），每次 SCREEN_WAKE 时检测
 *
 * 同时跟踪累计亮屏时长，通过 [checkLongUsage] 方法供外部查询。
 *
 * 实现要点：
 * - ACTION_SCREEN_ON / ACTION_SCREEN_OFF 在 API 26+ 上无法通过 Manifest 注册，必须动态注册
 * - Receiver 必须在生命周期独立于 Activity 的宿主中注册（如 ForegroundService），
 *   否则锁屏/亮屏时 Activity 正处于停止状态，Receiver 来不及收到广播
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
     * totalAccumulatedMs: 之前所有亮屏段的累计时长（持久化）
     * longUsageEmitted: 今日是否已经发射过 LONG_USAGE（持久化）
     */
    private var sessionStartTime: Long = 0L
    private var totalAccumulatedMs: Long = 0L
    private var longUsageEmitted: Boolean = false

    /** 深夜发射标记：同一段深夜范围只发射一次 */
    private var lateNightEmitted: Boolean = false

    /** 持久化存储 */
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 上次持久化的日期（用于跨天重置），格式 yyyy-MM-dd */
    private var lastResetDate: String = ""
    private val todayDate: String
        get() = DATE_FORMAT.format(Date())

    override fun start() {
        if (isStarted) {
            Log.w(TAG, "ScreenMonitor already started, ignoring duplicate start()")
            return
        }
        isStarted = true
        sessionStartTime = 0L
        lateNightEmitted = false

        // ── 恢复持久化状态，检查跨天重置 ──
        restoreState()

        // ══════════════════════════════════════════════════════════
        // 兜底检测：Service 可能在深度休眠期间被系统杀死，
        // 此时 ACTION_SCREEN_ON 广播已发送完毕，BroadcastReceiver
        // 重建后永远收不到该广播。通过 PowerManager 检测当前
        // 屏幕状态来补偿丢失的事件。
        // ══════════════════════════════════════════════════════════
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val isScreenOn = pm.isInteractive
        Log.i(TAG, "Initial screen state: ${if (isScreenOn) "ON (interactive)" else "OFF (non-interactive)"}" +
                " | accumulated=${totalAccumulatedMs}ms, longUsageEmitted=$longUsageEmitted")

        if (isScreenOn) {
            // 屏幕当前是亮的，但我们没有 sessionStartTime，
            // 说明错过了 ACTION_SCREEN_ON 广播（Service 刚重建或首次启动）。
            // 补偿：初始化亮屏计时 + 发射 ScreenWake 事件
            sessionStartTime = SystemClock.elapsedRealtime()
            Log.i(TAG, "Fallback: initializing screen-on state (missed broadcast compensated)")

            // 深夜检测（与 onReceive 中的逻辑保持一致）
            if (!lateNightEmitted && isLateNightHour()) {
                lateNightEmitted = true
                val now = System.currentTimeMillis()
                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                _events.tryEmit(DeviceEvent.LateNight)
                AtomicEventBus.tryEmit(AtomicEvent.LateNight(now, hour))
                Log.i(TAG, "Event emitted (fallback): LateNight (hour=$hour)")
            }

            val now = System.currentTimeMillis()
            _events.tryEmit(DeviceEvent.ScreenWake)
            AtomicEventBus.tryEmit(AtomicEvent.ScreenOn(now))
            Log.i(TAG, "Event emitted (fallback): ScreenWake")
        }
        // 注意：isScreenOn == false 时不发射 ScreenOff，
        // 因为可能是 Service 在灭屏期间重建，未错过任何事件

        val screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                Log.d(TAG, "onReceive: action=${intent.action}")
                when (intent.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        Log.i(TAG, "SCREEN_ON detected")
                        val now = System.currentTimeMillis()
                        // 开始累计这一段的亮屏时长
                        sessionStartTime = SystemClock.elapsedRealtime()

                        // 深夜检测
                        if (!lateNightEmitted && isLateNightHour()) {
                            lateNightEmitted = true
                            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                            _events.tryEmit(DeviceEvent.LateNight)
                            AtomicEventBus.tryEmit(AtomicEvent.LateNight(now, hour))
                            Log.i(TAG, "Event emitted: LateNight (hour=$hour)")
                        }

                        _events.tryEmit(DeviceEvent.ScreenWake)
                        AtomicEventBus.tryEmit(AtomicEvent.ScreenOn(now))
                        Log.i(TAG, "Event emitted: ScreenWake")
                    }

                    Intent.ACTION_SCREEN_OFF -> {
                        Log.i(TAG, "SCREEN_OFF detected")
                        // 停止累计：将当前段的时长加入总累计
                        if (sessionStartTime > 0L) {
                            totalAccumulatedMs +=
                                SystemClock.elapsedRealtime() - sessionStartTime
                            sessionStartTime = 0L
                            // 持久化累计时长
                            persistState()
                        }

                        val now = System.currentTimeMillis()
                        _events.tryEmit(DeviceEvent.ScreenOff)
                        AtomicEventBus.tryEmit(AtomicEvent.ScreenOff(now))
                        Log.i(TAG, "Event emitted: ScreenOff")
                    }

                    Intent.ACTION_USER_PRESENT -> {
                        Log.d(TAG, "USER_PRESENT detected (device unlocked)")
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

        val registeredCount = filter.countActions()
        context.registerReceiver(screenReceiver, filter)
        receiver = screenReceiver

        Log.i(TAG, "ScreenMonitor started — listening for $registeredCount actions " +
                "(context=${context.javaClass.simpleName}@${System.identityHashCode(context)})")
    }

    override fun stop() {
        if (!isStarted) {
            Log.w(TAG, "ScreenMonitor not started, ignoring stop()")
            return
        }
        isStarted = false
        receiver?.let {
            try {
                context.unregisterReceiver(it)
                Log.i(TAG, "ScreenMonitor stopped — receiver unregistered")
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "ScreenMonitor stop: receiver already unregistered", e)
            }
        }
        receiver = null
        lateNightEmitted = false
        longUsageEmitted = false
        sessionStartTime = 0L
        totalAccumulatedMs = 0L
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
            persistState()  // 持久化触发标记
            val now = System.currentTimeMillis()
            _events.tryEmit(DeviceEvent.LongUsage(minutes = totalMinutes))
            AtomicEventBus.tryEmit(AtomicEvent.LongUsage(now, totalMinutes))
            Log.d(TAG, "Event emitted: LongUsage (${totalMinutes}min)")
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

    // ══════════════════════════════════════════════════════════════
    // 持久化（Service 重启后恢复状态）
    // ══════════════════════════════════════════════════════════════

    /**
     * 持久化当前状态到 SharedPreferences。
     * 在 SCREEN_OFF 和 LONG_USAGE 触发时调用。
     */
    private fun persistState() {
        prefs.edit()
            .putLong(KEY_ACCUMULATED_MS, totalAccumulatedMs)
            .putBoolean(KEY_LONG_USAGE_EMITTED, longUsageEmitted)
            .putString(KEY_LAST_RESET_DATE, todayDate)
            .apply()
    }

    /**
     * 从 SharedPreferences 恢复状态。
     * 如果是新的一天，重置累计时长和触发标记。
     */
    private fun restoreState() {
        lastResetDate = prefs.getString(KEY_LAST_RESET_DATE, "") ?: ""
        val persistedDate = lastResetDate

        if (persistedDate.isNotEmpty() && persistedDate != todayDate) {
            // 跨天 → 重置所有状态
            Log.i(TAG, "New day detected ($persistedDate → $todayDate) — resetting long usage state")
            totalAccumulatedMs = 0L
            longUsageEmitted = false
            lastResetDate = todayDate
            prefs.edit()
                .putLong(KEY_ACCUMULATED_MS, 0L)
                .putBoolean(KEY_LONG_USAGE_EMITTED, false)
                .putString(KEY_LAST_RESET_DATE, todayDate)
                .apply()
        } else {
            // 同一天 → 恢复已持久化的状态
            totalAccumulatedMs = prefs.getLong(KEY_ACCUMULATED_MS, 0L)
            longUsageEmitted = prefs.getBoolean(KEY_LONG_USAGE_EMITTED, false)
            if (persistedDate.isEmpty()) {
                lastResetDate = todayDate
            }
            if (totalAccumulatedMs > 0L || longUsageEmitted) {
                Log.i(TAG, "State restored: accumulated=${totalAccumulatedMs}ms, longUsageEmitted=$longUsageEmitted")
            }
        }
    }

    companion object {
        /** 统一调试 Tag */
        const val TAG = "MotionPetScreenMonitor"

        /** SharedPreferences 文件名 */
        private const val PREFS_NAME = "motionpet_screen_state"

        /** 持久化 Key：累计亮屏时长 (ms) */
        private const val KEY_ACCUMULATED_MS = "total_accumulated_ms"
        /** 持久化 Key：今日是否已触发 LONG_USAGE */
        private const val KEY_LONG_USAGE_EMITTED = "long_usage_emitted"
        /** 持久化 Key：上次重置日期 */
        private const val KEY_LAST_RESET_DATE = "last_reset_date"

        /** 日期格式（用于跨天检测） */
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }
}
