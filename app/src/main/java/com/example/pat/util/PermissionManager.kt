package com.example.pat.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * 权限管理工具。
 *
 * 集中管理应用运行时权限的检查和请求逻辑。
 * 当前仅处理各 API 级别下必需的权限，
 * 避免添加当前阶段不需要的权限声明。
 *
 * 权限说明：
 * - 加速度计/陀螺仪：Android 系统传感器，无需任何权限声明
 * - VIBRATE：Normal 权限，仅需 Manifest 声明，安装时自动授予
 * - POST_NOTIFICATIONS：Dangerous 权限（API 33+），需运行时请求
 * - FOREGROUND_SERVICE / RECEIVE_BOOT_COMPLETED：后续阶段添加
 * - 电池状态读取：无需权限（ACTION_BATTERY_CHANGED 为 sticky broadcast）
 */
object PermissionManager {

    /**
     * 检查是否已获取通知权限。
     * - API 33+（TIRAMISU）：需运行时检查 [Manifest.permission.POST_NOTIFICATIONS]
     * - API 32 以下：通知权限由系统自动授予，始终返回 true
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // API 32 以下无需运行时权限
        }
    }

    /**
     * 请求通知权限。
     * 使用 [ActivityResultContracts.RequestPermission] 是现代 Android 的推荐方式，
     * 但此方法提供直接请求入口供选择使用。
     *
     * @param activity 宿主 Activity
     * @param requestCode 请求码，用于 [Activity.onRequestPermissionsResult] 回调
     */
    fun requestNotificationPermission(activity: Activity, requestCode: Int = REQUEST_NOTIFICATION) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                requestCode
            )
        }
        // API 32 以下无需请求
    }

    /** 默认通知权限请求码 */
    const val REQUEST_NOTIFICATION = 1001
}
