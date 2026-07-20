package com.example.pat.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * 开机自启动接收器。
 *
 * 设备开机完成后自动启动 [CompanionForegroundService]，
 * 确保后台监测在重启后恢复运行。
 *
 * 注册方式：AndroidManifest.xml 静态注册。
 * 必要条件：RECEIVE_BOOT_COMPLETED 权限。
 *
 * 参考文档：4.2 BroadcastReceiver 说明
 */
class CompanionBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.i(TAG, "Boot completed — starting CompanionForegroundService")

        val serviceIntent = Intent(context, CompanionForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    companion object {
        private const val TAG = "CompanionBootReceiver"
    }
}
