package com.example.pat.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.example.pat.MainActivity
import com.example.pat.event.EventBus
import com.example.pat.monitor.DeviceStateMonitor
import com.example.pat.monitor.ScreenMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 前台服务 —— 承载所有设备状态监控器的后台宿主。
 *
 * 核心职责：
 * - 以 ForegroundService 形式保持进程存活，不受 Activity 生命周期影响
 * - 内部创建并管理 [DeviceStateMonitor]（ScreenMonitor + BatteryMonitor）
 * - 将所有 [DeviceEvent] 转发到全局 [EventBus]
 *
 * 为什么需要 ForegroundService？
 * ```
 * Activity.onStop() → Receiver 被注销 → 错过 SCREEN_ON/OFF 广播
 * ForegroundService   → Receiver 持续存活 → 正确收到所有广播
 * ```
 *
 * 生命周期：
 * ```
 * onCreate  → 创建通知渠道 + 初始化 DeviceStateMonitor
 * onStartCommand → startForeground() + 启动所有监控器
 * onDestroy → 停止所有监控器 + 释放资源
 * ```
 *
 * 通知：
 * - 显示持久通知以满足 Android 前台服务要求
 * - 点击通知返回 MainActivity
 */
class MonitorForegroundService : Service() {

    private lateinit var deviceStateMonitor: DeviceStateMonitor
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var eventForwardJob: Job? = null

    // ── 生命周期 ──

    override fun onCreate() {
        super.onCreate()
        Log.w(TAG, "╔══ Service onCreate — process may have been restarted after deep sleep ══╗")
        Log.i(TAG, "Service instance: ${this.javaClass.simpleName}@${System.identityHashCode(this)}")

        // 必须在 startForeground() 之前创建通知渠道
        createNotificationChannel()

        // 初始化设备状态监控器
        // 使用 Service context（非 Activity context），确保 Receiver 生命周期独立
        deviceStateMonitor = DeviceStateMonitor(this, serviceScope)
        Log.i(TAG, "DeviceStateMonitor initialized")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "Service onStartCommand (flags=$flags, startId=$startId, " +
                "action=${intent?.action})")

        // 启动前台服务（必须 5 秒内调用，否则系统强行停止）
        try {
            startForeground(NOTIFICATION_ID, buildNotification())
            Log.i(TAG, "Foreground notification shown (id=$NOTIFICATION_ID)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground", e)
            // POST_NOTIFICATIONS 权限未授予时可能失败
            // 降级：仍然尝试注册 Receiver，但系统可能随时杀死进程
        }

        // 启动设备监控
        deviceStateMonitor.start()
        Log.i(TAG, "DeviceStateMonitor started (Battery + Screen monitors active)")

        // 将所有设备事件转发到全局 EventBus
        eventForwardJob?.cancel()
        eventForwardJob = serviceScope.launch {
            deviceStateMonitor.events.collect { event ->
                Log.d(TAG, "Forwarding event to EventBus: ${event.javaClass.simpleName}")
                EventBus.tryEmit(event)
            }
        }

        // START_STICKY：Service 被杀死后自动重建（不含原 Intent）
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.w(TAG, "╚══ Service onDestroy — service is being killed ══╝")

        // 停止前最后检查一次 LONG_USAGE
        deviceStateMonitor.screenMonitor.checkLongUsage()

        // 取消事件转发
        eventForwardJob?.cancel()
        eventForwardJob = null

        // 停止监控器
        deviceStateMonitor.stop()
        Log.i(TAG, "DeviceStateMonitor stopped")

        // 释放协程资源
        serviceScope.cancel()

        super.onDestroy()
    }

    // ── 通知相关 ──

    /**
     * 创建通知渠道（API 26+ 必需）。
     * 幂等：重复调用不会创建重复渠道。
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW  // LOW：不发出声音，仅显示图标
        ).apply {
            description = CHANNEL_DESCRIPTION
            setShowBadge(false)
        }

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
        Log.i(TAG, "Notification channel created: $CHANNEL_ID")
    }

    /**
     * 构建前台服务持久通知。
     */
    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(NOTIFICATION_TITLE)
                .setContentText(NOTIFICATION_TEXT)
                .setSmallIcon(android.R.drawable.ic_menu_info_details) // 使用系统图标，避免缺失资源
                .setContentIntent(pendingIntent)
                .setOngoing(true)  // 持久通知，用户不可滑动删除
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle(NOTIFICATION_TITLE)
                .setContentText(NOTIFICATION_TEXT)
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        }
    }

    companion object {
        /** 统一调试 Tag：与 ScreenMonitor 保持一致 */
        private const val TAG = "MotionPetScreenMonitor"

        // 通知渠道
        private const val CHANNEL_ID = "monitor_service"
        private const val CHANNEL_NAME = "Companion Service"
        private const val CHANNEL_DESCRIPTION = "Shows when device monitoring is active"

        // 通知内容
        private const val NOTIFICATION_TITLE = "Pat Companion"
        private const val NOTIFICATION_TEXT = "Monitoring device state…"

        // 前台通知 ID
        private const val NOTIFICATION_ID = 1001
    }
}
