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
import com.example.pat.data.EventConfigRepository
import com.example.pat.data.PresetRepository
import com.example.pat.event.DeviceEvent
import com.example.pat.event.EventBus
import com.example.pat.model.EventConfig
import com.example.pat.service.CompanionForegroundService
import com.example.pat.ui.EditEventScreen
import com.example.pat.ui.EventListScreen
import com.example.pat.ui.HomeScreen
import com.example.pat.ui.PresetEditScreen
import com.example.pat.ui.navigation.Screen
import com.example.pat.ui.theme.PatTheme
import com.example.pat.util.PermissionManager

/**
 * MotionPet 主 Activity —— 纯 UI 层。
 */
class MainActivity : ComponentActivity() {

    // ── 数据层（Activity 级别，供所有 Composable 使用） ──
    private lateinit var presetRepository: PresetRepository
    private lateinit var configRepository: EventConfigRepository

    // ── UI 状态 ──
    private var currentScreen by mutableStateOf<Screen>(Screen.Home)
    private var configs by mutableStateOf<List<EventConfig>>(emptyList())
    private var todayTriggerCount by mutableIntStateOf(0)
    private var refreshTrigger by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestNotificationPermission()

        // 初始化数据层
        presetRepository = PresetRepository(this)
        configRepository = EventConfigRepository(this, presetRepository)

        // 启动后台服务
        startCompanionService()

        // 加载配置
        configs = configRepository.loadAll()

        setContent {
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
                                configs = configRepository.loadAll()
                                currentScreen = Screen.EventList
                            },
                            onTestEvent = { eventType ->
                                val testEvent = when (eventType) {
                                    com.example.pat.event.EventType.SCREEN_LONG_USAGE ->
                                        DeviceEvent.LongUsage(minutes = 999)
                                    com.example.pat.event.EventType.CHARGE_START ->
                                        DeviceEvent.ChargeStart
                                    com.example.pat.event.EventType.LOW_BATTERY ->
                                        DeviceEvent.LowBattery
                                    com.example.pat.event.EventType.SHAKE ->
                                        DeviceEvent.Shake
                                    com.example.pat.event.EventType.IMPACT ->
                                        DeviceEvent.Impact(intensity = 1.0f)
                                }
                                EventBus.tryEmit(testEvent)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    is Screen.EventList -> {
                        EventListScreen(
                            configs = configs,
                            presetRepository = presetRepository,
                            onToggleEnabled = { config ->
                                configRepository.save(config)
                                configs = configRepository.loadAll()
                            },
                            onEditClick = { eventType ->
                                configs = configRepository.loadAll()
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
                                presetRepository = presetRepository,
                                onCreateCustomPreset = { eventType ->
                                    currentScreen = Screen.EditPreset(eventType)
                                },
                                onSave = { updatedConfig ->
                                    configRepository.save(updatedConfig)
                                    configs = configRepository.loadAll()
                                    currentScreen = Screen.EventList
                                },
                                onPreviewAsset = { assetPath ->
                                    val service = CompanionForegroundServiceHolder.instance
                                    service?.responseManager?.stopVoice()
                                    val ctx = this@MainActivity
                                    if (assetPath.startsWith("/") || assetPath.startsWith(ctx.filesDir.absolutePath)) {
                                        com.example.pat.response.VoiceService(ctx).play(assetPath)
                                    } else {
                                        com.example.pat.audio.AudioPlayer(ctx).playAsset(assetPath)
                                    }
                                },
                                onBack = {
                                    configs = configRepository.loadAll()
                                    currentScreen = Screen.EventList
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    is Screen.EditPreset -> {
                        val editPreset = screen as Screen.EditPreset
                        val existingPreset = editPreset.presetId?.let {
                            presetRepository.getById(it)
                        }
                        val ctx = this@MainActivity
                        PresetEditScreen(
                            eventTypeName = EventConfig.displayName(editPreset.eventType),
                            existingPreset = existingPreset,
                            onSave = { preset ->
                                presetRepository.saveCustom(
                                    preset.copy(eventType = editPreset.eventType)
                                )
                                val config = configRepository.getByEventType(editPreset.eventType)
                                if (config != null) {
                                    configRepository.save(config.copy(presetId = preset.id))
                                    configs = configRepository.loadAll()
                                }
                                currentScreen = Screen.EditEvent(editPreset.eventType)
                            },
                            onPreviewAsset = { assetPath ->
                                if (assetPath.startsWith("/")) {
                                    com.example.pat.response.VoiceService(ctx).play(assetPath)
                                } else {
                                    com.example.pat.audio.AudioPlayer(ctx).playAsset(assetPath)
                                }
                            },
                            onBack = {
                                currentScreen = Screen.EditEvent(editPreset.eventType)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                }
            }
        }

        Log.i(TAG, "MainActivity created")
    }

    override fun onResume() {
        super.onResume()
        configs = configRepository.loadAll()
        refreshTrigger++
    }

    private fun startCompanionService() {
        val intent = Intent(this, CompanionForegroundService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
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
 * 持有对运行中 Service 实例的引用。
 */
object CompanionForegroundServiceHolder {
    @Volatile
    var instance: CompanionForegroundService? = null
}
