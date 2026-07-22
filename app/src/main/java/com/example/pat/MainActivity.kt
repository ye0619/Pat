package com.example.pat

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import com.example.pat.data.EventConfigRepository
import com.example.pat.data.EventDefinitionRepository
import com.example.pat.data.PresetRepository
import com.example.pat.model.EventConfig
import com.example.pat.model.EventDefinition
import com.example.pat.service.CompanionForegroundService
import com.example.pat.ui.EditEventScreen
import com.example.pat.ui.EventListScreen
import com.example.pat.ui.HomeScreen
import com.example.pat.ui.ClickTracker
import com.example.pat.ui.NewEventScreen
import com.example.pat.ui.PresetEditScreen
import com.example.pat.ui.navigation.Screen
import com.example.pat.ui.theme.PatTheme
import com.example.pat.util.PermissionManager

class MainActivity : ComponentActivity() {

    private lateinit var presetRepository: PresetRepository
    private lateinit var configRepository: EventConfigRepository
    private lateinit var definitionRepository: EventDefinitionRepository

    private var currentScreen by mutableStateOf<Screen>(Screen.Home)
    private var configs by mutableStateOf<List<EventConfig>>(emptyList())
    private var customDefs by mutableStateOf<List<EventDefinition>>(emptyList())
    private var conflictDefIds by mutableStateOf<Set<String>>(emptySet())
    private var todayTriggerCount by mutableIntStateOf(0)
    private var refreshTrigger by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        requestNotificationPermission()

        presetRepository = PresetRepository(this)
        configRepository = EventConfigRepository(this, presetRepository)
        definitionRepository = EventDefinitionRepository(this)
        startCompanionService()
        configs = configRepository.loadAll()
        customDefs = definitionRepository.loadAll()
        conflictDefIds = computeConflicts()

        setContent {
            val screen = currentScreen; refreshTrigger

            // 监听服务端数据变化，实时刷新 UI
            LaunchedEffect(Unit) {
                while (true) {
                    val svc = CompanionForegroundServiceHolder.instance
                    if (svc != null) {
                        try {
                            svc.ruleEngineV2.uiRefresh.collect {
                                todayTriggerCount = svc.ruleEngineV2.todayTriggerCount
                                refreshTrigger++
                            }
                        } catch (_: Exception) { }
                    }
                    delay(300)
                }
            }

            PatTheme {
                ClickTracker {
                when (screen) {
                    is Screen.Home -> {
                        val svc = CompanionForegroundServiceHolder.instance
                        HomeScreen(isServiceRunning = svc != null,
                            todayTriggerCount = svc?.ruleEngineV2?.todayTriggerCount ?: todayTriggerCount,
                            recentTriggers = svc?.ruleEngineV2?.recentTriggers ?: emptyList(),
                            onNavigateToEventList = { reloadData(); currentScreen = Screen.EventList },
                            modifier = Modifier.fillMaxSize())
                    }
                    is Screen.EventList -> EventListScreen(
                        configs = configs, customDefs = customDefs,
                        presetRepository = presetRepository, conflictDefIds = conflictDefIds,
                        onToggleConfig = { c -> configRepository.save(c); reloadData() },
                        onToggleDef = { d -> definitionRepository.save(d); reloadData() },
                        onEditConfig = { c -> currentScreen = Screen.EditEvent(c.eventType) },
                        onEditDef = { d -> currentScreen = Screen.NewEvent(d.id) },
                        onDeleteDef = { d -> definitionRepository.delete(d.id); reloadData() },
                        onCreateClick = { currentScreen = Screen.NewEvent(null) },
                        onRestoreDefaults = { configRepository.restoreDefaults(); reloadData() },
                        onBack = { currentScreen = Screen.Home },
                        modifier = Modifier.fillMaxSize()
                    )
                    is Screen.EditEvent -> {
                        val es = screen as Screen.EditEvent
                        val cfg = configs.find { it.eventType == es.eventType }
                        if (cfg != null) EditEventScreen(
                            config = cfg, presetRepository = presetRepository,
                            onCreateCustomPreset = { currentScreen = Screen.EditPreset(it) },
                            onSave = { configRepository.save(it); configs = configRepository.loadAll(); currentScreen = Screen.EventList },
                            onPreviewAsset = { preview(it) },
                            onRestoreDefaults = { et -> configRepository.restoreSingle(et); configs = configRepository.loadAll() },
                            onDeletePreset = { presetId ->
                                presetRepository.deleteCustom(presetId)
                                // 如果当前事件引用了被删除的预设，回退到第一个可用预设
                                val current = configRepository.getByEventType(es.eventType)
                                if (current != null && current.presetId == presetId) {
                                    val fallback = presetRepository.getByEventType(es.eventType).firstOrNull()
                                    configRepository.save(current.copy(presetId = fallback?.id ?: ""))
                                }
                                configs = configRepository.loadAll()
                            },
                            onBack = { configs = configRepository.loadAll(); currentScreen = Screen.EventList },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    is Screen.EditPreset -> {
                        val ep = screen as Screen.EditPreset
                        PresetEditScreen(
                            eventTypeName = EventConfig.displayName(ep.eventType),
                            existingPreset = ep.presetId?.let { presetRepository.getById(it) },
                            onSave = { pre ->
                                presetRepository.saveCustom(pre.copy(eventType = ep.eventType))
                                configRepository.getByEventType(ep.eventType)?.let {
                                    configRepository.save(it.copy(presetId = pre.id))
                                }; configs = configRepository.loadAll()
                                currentScreen = Screen.EditEvent(ep.eventType)
                            },
                            onPreviewAsset = { preview(it) },
                            onBack = { currentScreen = Screen.EditEvent(ep.eventType) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    is Screen.NewEvent -> {
                        val ne = screen as Screen.NewEvent
                        NewEventScreen(
                            existing = ne.defId?.let { definitionRepository.loadAll().find { d -> d.id == it } },
                            onSave = { def ->
                                definitionRepository.save(def); reloadData()
                                CompanionForegroundServiceHolder.instance?.ruleEngineV2?.reloadRules()
                                currentScreen = Screen.EventList
                            },
                            onBack = { currentScreen = Screen.EventList },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                }
            }
        }
        Log.i(TAG, "MainActivity v4 unified events created")
    }

    override fun onResume() { super.onResume(); reloadData(); refreshTrigger++ }

    private fun reloadData() {
        configs = configRepository.loadAll()
        customDefs = definitionRepository.loadAll()
        conflictDefIds = computeConflicts()
    }

    private fun computeConflicts(): Set<String> {
        val enabled = customDefs.filter { it.enabled }
        val conflicted = mutableSetOf<String>()
        for (i in enabled.indices) for (j in i + 1 until enabled.size) {
            val ta = enabled[i].conditions.map { it.atomicType }.toSet()
            val tb = enabled[j].conditions.map { it.atomicType }.toSet()
            if (ta.intersect(tb).isNotEmpty()) { conflicted.add(enabled[i].id); conflicted.add(enabled[j].id) }
        }
        return conflicted
    }

    private fun preview(path: String) {
        CompanionForegroundServiceHolder.instance?.responseManager?.stopVoice()
        if (path.startsWith("/") || path.startsWith(filesDir.absolutePath))
            com.example.pat.response.VoiceService(this).play(path)
        else com.example.pat.audio.AudioPlayer(this).playAsset(path)
    }

    private fun startCompanionService() {
        val i = Intent(this, CompanionForegroundService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
            startForegroundService(i) else startService(i)
    }

    private fun requestNotificationPermission() {
        if (!PermissionManager.hasNotificationPermission(this))
            PermissionManager.requestNotificationPermission(this)
    }

    companion object { private const val TAG = "MotionPet" }
}

object CompanionForegroundServiceHolder {
    @Volatile var instance: CompanionForegroundService? = null
}
