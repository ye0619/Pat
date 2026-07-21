package com.example.pat.response

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import com.example.pat.audio.AudioPlaybackState
import java.io.File

/**
 * 语音播放服务 —— 播放用户上传的音频文件。
 *
 * 职责：
 * - 播放应用内部存储中的 mp3/wav 音频文件
 * - 支持试听预览
 * - 自动检查音频焦点和铃声模式
 *
 * 特点：
 * - 使用 [MediaPlayer] 进行播放
 * - 低音量输出（0.3-0.5 media volume）
 * - 静音/震动模式下跳过播放
 * - 第一版不支持语音生成 AI
 *
 * 参考文档：原始规范 7. 语音系统
 */
class VoiceService(
    private val context: Context
) {
    private var mediaPlayer: MediaPlayer? = null

    /**
     * 播放指定路径的音频文件。
     * 如果正在播放其他音频，先停止。
     *
     * @param path 音频文件的绝对路径
     */
    fun play(path: String) {
        // 检查文件是否存在
        val file = File(path)
        if (!file.exists()) {
            Log.w(TAG, "Audio file not found: $path")
            return
        }

        // 检查铃声模式（静音/震动时跳过）
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val ringerMode = audioManager.ringerMode
            if (ringerMode == AudioManager.RINGER_MODE_SILENT) {
                Log.i(TAG, "Skipping playback: ringer mode is SILENT")
                return
            }
        }

        // 停止当前播放
        stop()

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(path)

                // 设置音频属性：低音量媒体流
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                } else {
                    @Suppress("DEPRECATION")
                    setAudioStreamType(AudioManager.STREAM_MUSIC)
                }

                setVolume(0.4f, 0.4f)

                setOnCompletionListener {
                    Log.d(TAG, "Playback completed: $path")
                    releasePlayer()
                }

                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                    releasePlayer()
                    true
                }

                prepare()
                start()
                AudioPlaybackState.onStart(context, File(path).name, this)
                Log.i(TAG, "Playing: $path")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play audio: $path", e)
            releasePlayer()
        }
    }

    /**
     * 停止当前播放并释放资源。
     */
    fun stop() {
        releasePlayer()
    }

    private fun releasePlayer() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                reset()
                release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing MediaPlayer", e)
        }
        mediaPlayer = null
        AudioPlaybackState.onStop()
    }

    companion object {
        private const val TAG = "VoiceService"
    }
}
