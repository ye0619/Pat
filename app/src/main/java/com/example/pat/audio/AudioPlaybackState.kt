package com.example.pat.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.pat.MainActivity
import com.example.pat.service.CompanionForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 全局音频播放状态管理器。
 *
 * 职责：
 * - 跟踪当前播放状态（名称、是否播放中）
 * - 提供暂停/继续/停止操作
 * - 显示播放通知（含停止按钮）
 *
 * 由 [AudioPlayer] / [VoiceService] 在开始/停止播放时更新，
 * HomeScreen 通过 [isPlaying] / [currentName] 观察状态。
 */
object AudioPlaybackState {

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentName = MutableStateFlow("")
    val currentName: StateFlow<String> = _currentName.asStateFlow()

    /** 当前播放器引用（用于暂停/停止） */
    private var player: android.media.MediaPlayer? = null
    private var appContext: Context? = null

    /** 播放开始 */
    fun onStart(context: Context, name: String, mediaPlayer: android.media.MediaPlayer) {
        onStop() // 停止之前的
        appContext = context.applicationContext
        player = mediaPlayer
        _currentName.value = name
        _isPlaying.value = true
        showNotification(context, name)
    }

    /** 播放停止 */
    fun onStop() {
        player?.let {
            try { if (it.isPlaying) it.stop() } catch (_: Exception) {}
            try { it.reset() } catch (_: Exception) {}
            try { it.release() } catch (_: Exception) {}
        }
        player = null
        _isPlaying.value = false
        _currentName.value = ""
        dismissNotification()
    }

    /** 暂停/继续切换 */
    fun togglePause() {
        val p = player ?: return
        try {
            if (p.isPlaying) {
                p.pause()
                _isPlaying.value = false
            } else {
                p.start()
                _isPlaying.value = true
            }
        } catch (_: Exception) {}
    }

    /** 通知管理 */
    private var notificationManager: NotificationManager? = null

    private fun showNotification(context: Context, name: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager = nm

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, "音频播放", NotificationManager.IMPORTANCE_LOW
                ).apply { setShowBadge(false); setSound(null, null); enableVibration(false) }
            )
        }

        val stopIntent = Intent(context, CompanionForegroundService::class.java).apply {
            action = CompanionForegroundService.ACTION_STOP_AUDIO
        }
        val stopPi = PendingIntent.getService(
            context, 100, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val contentIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Pat 正在播放")
            .setContentText(name)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setSilent(true)
            .addAction(android.R.drawable.ic_media_pause, "停止", stopPi)
            .build()

        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun dismissNotification() {
        notificationManager?.cancel(NOTIFICATION_ID)
        notificationManager = null
    }

    private const val CHANNEL_ID = "pat_audio_playback"
    private const val NOTIFICATION_ID = 4001
}
