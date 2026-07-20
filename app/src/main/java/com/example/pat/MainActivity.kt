package com.example.pat

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.pat.config.EventConfig
import com.example.pat.config.PreferenceManager
import com.example.pat.event.EventType
import com.example.pat.service.CompanionForegroundService
import com.example.pat.ui.EditEventScreen
import com.example.pat.ui.EventListScreen
import com.example.pat.ui.HomeScreen
import com.example.pat.ui.navigation.Screen
import com.example.pat.ui.theme.PatTheme
import com.example.pat.util.PermissionManager

/**
 * MotionPet 主 Activity —— 纯 UI 配置层。
 *
 * 所有业务逻辑在 [CompanionForegroundService] 中运行。
 * Activity 仅负责：
 * - 启动/绑定后台服务
 * - 提供配置编辑界面
 * - 展示运行状态和触发历史
 *
 * 参考文档：原始规范 5. 前端界面设计
 */
class MainActivity : ComponentActivity() {

    private lateinit var preferenceManager: PreferenceManager

    // ── UI 导航状态 ──
    private var currentScreen by mutableStateOf<Screen>(Screen.Home)
    private var configs by mutableStateOf<List<EventConfig>>(emptyList())
    private var todayTriggerCount by mutableIntStateOf(0)
    private var refreshTrigger by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 请求通知权限
        requestNotificationPermission()

        // 初始化配置管理器
        preferenceManager = PreferenceManager(this)

        // 启动后台服务
        startCompanionService()

        // 加载配置
        configs = preferenceManager.loadConfigs()

        setContent {
            // 当 refreshTrigger 变化时重新读取配置
            val currentTrigger = refreshTrigger
            val screen = currentScreen

            PatTheme {
                when (screen) {
                    is Screen.Home -> {
                        val service = CompanionForegroundServiceHolder.instance
                        HomeScreen(
                            isServiceRunning = service != null,
                            todayTriggerCount = if (service != null) {
                                service.eventDispatcher.todayTriggerCount
                            } else todayTriggerCount,
                            recentTriggers = if (service != null) {
                                service.eventDispatcher.recentTriggers
                            } else emptyList(),
                            onNavigateToEventList = {
                                configs = preferenceManager.loadConfigs()
                                currentScreen = Screen.EventList
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    is Screen.EventList -> {
                        EventListScreen(
                            configs = configs,
                            onToggleEnabled = { config ->
                                preferenceManager.saveConfig(config)
                                configs = preferenceManager.loadConfigs()
                            },
                            onEditClick = { eventType ->
                                configs = preferenceManager.loadConfigs()
                                currentScreen = Screen.EditEvent(eventType)
                            },
                            onBack = { currentScreen = Screen.Home },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    is Screen.EditEvent -> {
                        val editScreen = screen as Screen.EditEvent
                        val config = configs.find { it.eventType == editScreen.eventType }
                        if (config != null) {
                            EditEventScreen(
                                config = config,
                                onSave = { updatedConfig ->
                                    preferenceManager.saveConfig(updatedConfig)
                                    configs = preferenceManager.loadConfigs()
                                    currentScreen = Screen.EventList
                                },
                                onPreviewVoice = { path ->
                                    // 通过 Service 的 ResponseManager 试听
                                    CompanionForegroundServiceHolder.instance
                                        ?.let { svc ->
                                            svc.eventDispatcher.stop()
                                            // 直接创建临时播放器
                                            com.example.pat.response.VoiceService(this).play(path)
                                        }
                                },
                                onBack = {
                                    configs = preferenceManager.loadConfigs()
                                    currentScreen = Screen.EventList
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }

        Log.i(TAG, "MainActivity created — UI only, engine in Service")
    }

    override fun onResume() {
        super.onResume()
        // 刷新配置和服务状态
        configs = preferenceManager.loadConfigs()
        refreshTrigger++
    }

    /**
     * 启动 CompanionForegroundService。
     */
    private fun startCompanionService() {
        val intent = Intent(this, CompanionForegroundService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Log.i(TAG, "CompanionForegroundService start requested")
    }

    private fun requestNotificationPermission() {
        if (!PermissionManager.hasNotificationPermission(this)) {
            PermissionManager.requestNotificationPermission(this)
        }
    }

    companion object {
        private const val TAG = "MotionPet"
    }
}

/**
 * 持有对运行中 Service 实例的引用，供 Activity 获取状态。
 */
object CompanionForegroundServiceHolder {
    @Volatile
    var instance: CompanionForegroundService? = null
}
