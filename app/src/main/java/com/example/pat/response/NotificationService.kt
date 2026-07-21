package com.example.pat.response

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.pat.MainActivity

/**
 * 通知服务 —— 发送事件反馈通知（Heads-up 弹窗风格，类似微信消息）。
 *
 * 特点：
 * - 使用 IMPORTANCE_HIGH 渠道，确保以 Heads-up 弹窗形式显示
 * - 显式设置声音 URI + 震动 pattern（兼容各 OEM 定制 ROM）
 * - 支持用户配置：声音开关、震动开关、横幅开关、锁屏可见性
 * - 渠道 ID 含版本号，安装新版本时自动重建渠道配置
 * - 点击通知打开主界面，自动消失
 *
 * 参考文档：原始规范 8. 通知系统
 */
class NotificationService(
    private val context: Context
) {
    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /** 系统默认通知音效 URI */
    private val defaultSoundUri: Uri =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    init {
        createChannel()
    }

    /**
     * 显示事件反馈通知（Heads-up 弹窗风格）。
     *
     * @param title 通知标题
     * @param text 通知内容（来自预设文本）
     * @param enableSound 是否播放系统通知音效
     * @param enableVibration 是否震动（默认关闭）
     * @param showHeadsUp 是否以 Heads-up 横幅显示
     * @param lockScreenPublic 锁屏时是否显示通知内容
     */
    fun show(
        title: String,
        text: String,
        enableSound: Boolean = true,
        enableVibration: Boolean = false,
        showHeadsUp: Boolean = true,
        lockScreenPublic: Boolean = true
    ) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(
                if (lockScreenPublic) NotificationCompat.VISIBILITY_PUBLIC
                else NotificationCompat.VISIBILITY_PRIVATE
            )

        // ── 优先级：横幅 vs 普通通知 ──
        if (showHeadsUp) {
            builder.setPriority(NotificationCompat.PRIORITY_HIGH)
        } else {
            builder.setPriority(NotificationCompat.PRIORITY_DEFAULT)
        }

        // ── 声音 ──
        if (enableSound) {
            builder.setSound(defaultSoundUri)
            // Android 8+ 渠道已设置声音，此处通知级设置作为兜底兼容
        } else {
            builder.setSound(null)
        }

        // ── 震动（默认关闭） ──
        if (enableVibration) {
            builder.setVibrate(VIBRATION_PATTERN)
        } else {
            // 显式传入 null 禁止震动（覆盖渠道默认值）
            builder.setVibrate(longArrayOf(0))
            // 也尝试设置静音震动模式
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setVibrate(null)
            }
        }

        // ── 灯光 ──
        builder.setLights(0xFF_6B4C9A.toInt(), 500, 1000)

        // ── 时间戳（确保每次通知被视为新通知，而非更新旧通知） ──
        builder.setWhen(System.currentTimeMillis())

        val notification = builder.build()

        // 确保 Heads-up 属性生效
        if (showHeadsUp) {
            @Suppress("DEPRECATION")
            notification.flags = notification.flags or Notification.FLAG_HIGH_PRIORITY
        }

        notificationManager.notify(nextId(), notification)
        Log.i(TAG, "Notification posted: \"$text\" (sound=$enableSound vibrate=$enableVibration headsUp=$showHeadsUp)")
    }

    /**
     * 创建 HIGH 重要性通知渠道。
     *
     * 关键设计：渠道级不预设声音/震动，全部由通知级 [show] 方法参数控制。
     * 这样用户可在每个事件上独立开关声音/震动，而不受渠道预设限制。
     *
     * 渠道 ID 含 v2 标记，确保安装新版 APK 时重建渠道
     * （Android 不允许修改已创建渠道的 importance/sound/vibration）。
     */
    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = CHANNEL_DESCRIPTION
            // 不在渠道级预设声音 —— 由通知级根据用户偏好控制
            setSound(null, null)
            // 不在渠道级预设震动 —— 由通知级根据用户偏好控制
            enableVibration(false)
            // 灯光
            enableLights(true)
            lightColor = 0xFF_6B4C9A.toInt()
            // 锁屏可见性：默认显示全部内容（通知级可覆盖）
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            // 显示角标
            setShowBadge(true)
        }
        notificationManager.createNotificationChannel(channel)
        Log.i(TAG, "Notification channel created: $CHANNEL_ID (IMPORTANCE_HIGH, no preset sound/vibration)")
    }

    private var notificationId = 3000
    private fun nextId(): Int = notificationId++

    companion object {
        private const val TAG = "NotificationService"

        /** 通知渠道 ID（v2 强制重建渠道配置） */
        const val CHANNEL_ID = "pat_event_alerts_v2"

        /** 渠道显示名称 */
        const val CHANNEL_NAME = "Pat 事件提醒"

        /** 渠道描述 */
        const val CHANNEL_DESCRIPTION = "Pat 事件反馈通知（弹窗横幅 + 声音 + 震动）"

        /** 震动模式：延迟 0ms，震动 200ms，暂停 100ms，震动 300ms */
        val VIBRATION_PATTERN = longArrayOf(0, 200, 100, 300)
    }
}
