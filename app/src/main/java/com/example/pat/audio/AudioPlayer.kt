package com.example.pat.audio

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import java.io.IOException

/**
 * 音频播放器 —— 播放 assets/ 中的预设音频资源。
 *
 * 职责：
 * - 通过 AssetManager 播放 assets/ 中的音频文件
 * - 停止当前播放
 * - 释放 MediaPlayer 资源
 *
 * 与 [com.example.pat.response.VoiceService] 的区别：
 * - VoiceService 播放用户上传的音频（通过文件路径）
 * - AudioPlayer 播放内置预设音频（通过 assets 路径）
 *
 * 使用方式：
 * ```
 * val player = AudioPlayer(context)
 * player.playAsset("长时间使用（别看了，我想睡觉了）.wav")
 * // ...
 * player.release()
 * ```
 *
 * 参考：Task 6 - 音频播放模块
 */
class AudioPlayer(
    private val context: Context
) {
    private var mediaPlayer: MediaPlayer? = null

    /**
     * 播放 assets/ 中指定路径的音频文件。
     * 如果正在播放其他音频，先停止。
     *
     * @param assetPath assets 中的文件路径（如 "长时间使用（别看了，我想睡觉了）.wav"）
     */
    fun playAsset(assetPath: String) {
        if (assetPath.isBlank()) {
            Log.w(TAG, "Empty asset path")
            return
        }

        // 检查铃声模式（静音时跳过）
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT) {
                Log.i(TAG, "Skipping playback: ringer mode is SILENT")
                return
            }
        }

        // 停止当前播放
        stop()

        try {
            val afd: AssetFileDescriptor = context.assets.openFd(assetPath)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()

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
                    Log.d(TAG, "Playback completed: $assetPath")
                    releasePlayer()
                }

                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                    releasePlayer()
                    true
                }

                prepare()
                start()
                AudioPlaybackState.onStart(context, assetPath, this)
                Log.i(TAG, "Playing preset audio: $assetPath")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Asset not found: $assetPath", e)
            releasePlayer()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play preset audio: $assetPath", e)
            releasePlayer()
        }
    }

    /**
     * 停止当前播放并释放资源。
     */
    fun stop() {
        releasePlayer()
    }

    /**
     * 释放 MediaPlayer 资源。
     * 调用后此 AudioPlayer 实例仍可继续使用（下次 play 会创建新 MediaPlayer）。
     */
    fun release() {
        releasePlayer()
    }

    /** 是否正在播放 */
    val isPlaying: Boolean
        get() = mediaPlayer?.isPlaying == true

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
        private const val TAG = "AudioPlayer"
    }
}
