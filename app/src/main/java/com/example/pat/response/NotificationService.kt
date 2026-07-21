package com.example.pat.response

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.pat.MainActivity

/**
 * 通知服务 —— 发送事件反馈通知（Heads-up 弹窗风格，类似微信消息）。
 *
 * 特点：
 * - 使用 IMPORTANCE_HIGH 渠道，确保以 Heads-up 弹窗形式显示
 * - 点击通知打开主界面
 * - 自动消失（autoCancel）
 *
 * 参考文档：原始规范 8. 通知系统
 */
class NotificationService(
    private val context: Context
) {
    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannel()
    }

    /**
     * 显示事件反馈通知（Heads-up 弹窗风格）。
     *
     * @param title 通知标题
     * @param text 通知内容（来自预设文本）
     */
    fun show(title: String, text: String) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setDefaults(android.app.Notification.DEFAULT_ALL)
            .build()

        notificationManager.notify(nextId(), notification)
        Log.i(TAG, "Heads-up notification posted: \"$text\"")
    }

    /**
     * 创建 HIGH 重要性通知渠道 —— 确保以弹窗形式显示。
     */
    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = CHANNEL_DESCRIPTION
            setShowBadge(true)
            enableLights(true)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private var notificationId = 2000
    private fun nextId(): Int = notificationId++

    companion object {
        private const val TAG = "NotificationService"
        const val CHANNEL_ID = "motionpet_events"
        const val CHANNEL_NAME = "MotionPet 反馈"
        const val CHANNEL_DESCRIPTION = "MotionPet 事件反馈通知（弹窗提醒）"
    }
}
