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
 * 通知服务 —— 发送事件反馈通知。
 *
 * 使用独立的通知渠道（区分于前台服务的常驻通知）。
 * 通知内容来自用户配置的反馈文本。
 *
 * 特点：
 * - 用户可设置是否显示通知
 * - 点击通知打开主界面
 * - 低优先级，不打扰用户
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
     * 显示事件反馈通知。
     *
     * @param title 通知标题
     * @param text 通知内容（来自用户配置）
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
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(nextId(), notification)
        Log.i(TAG, "Notification posted: \"$text\"")
    }

    /**
     * 创建事件反馈通知渠道。
     * 与前台服务通知渠道独立。
     */
    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = CHANNEL_DESCRIPTION
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    /** 自增通知 ID，避免覆盖 */
    private var notificationId = 2000
    private fun nextId(): Int = notificationId++

    companion object {
        private const val TAG = "NotificationService"

        const val CHANNEL_ID = "motionpet_events"
        const val CHANNEL_NAME = "MotionPet 反馈"
        const val CHANNEL_DESCRIPTION = "MotionPet 事件反馈通知"
    }
}
