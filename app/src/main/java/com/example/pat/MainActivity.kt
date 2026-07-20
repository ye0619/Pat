package com.example.pat

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.pat.behavior.BehaviorController
import com.example.pat.detector.DropDetector
import com.example.pat.detector.DropResult
import com.example.pat.detector.ImpactDetector
import com.example.pat.detector.ImpactResult
import com.example.pat.detector.ShakeDetector
import com.example.pat.event.EventBus
import com.example.pat.event.MotionEvent
import com.example.pat.sensor.MotionSensorManager
import com.example.pat.ui.PetScreen
import com.example.pat.ui.theme.PatTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/**
 * 主 Activity。
 *
 * 职责：
 * - 初始化传感器管理、检测器、行为控制器
 * - 建立数据管道：sensor → detector → event → behavior → UI
 * - 生命周期感知的传感器采集管理
 *
 * 架构约束：
 * - Activity 仅负责组装模块，不实现检测或业务逻辑
 * - 传感器数据不直接流入 UI，严格遵循分层方向
 *
 * 参考文档：
 * - 3.3 数据流图设计
 * - 9.2 生命周期管理
 */
class MainActivity : ComponentActivity() {

    // 核心模块实例
    private lateinit var sensorManager: MotionSensorManager
    private val impactDetector = ImpactDetector()
    private val shakeDetector = ShakeDetector()
    private val dropDetector = DropDetector()
    private val behaviorController = BehaviorController()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 初始化传感器管理器
        sensorManager = MotionSensorManager(this)

        // 启动传感器采集管道
        startSensorPipeline()

        setContent {
            PatTheme {
                PetScreen(
                    behaviorController = behaviorController,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    /**
     * 建立数据管道：
     *
     * Accelerometer Flow
     *   → ImpactDetector / ShakeDetector / DropDetector
     *   → EventBus (MotionEvent)
     *   → BehaviorController.handleEvent()
     *   → StateFlow<BehaviorSnapshot> → UI
     *
     * 使用 lifecycleScope 确保：
     * - Activity 可见时自动收集
     * - Activity 不可见时自动取消，停止传感器采集
     * - 无需手动管理 register/unregister
     */
    private fun startSensorPipeline() {
        lifecycleScope.launch {
            sensorManager.accelerometerFlow
                .flowOn(Dispatchers.Default)
                .catch { e ->
                    Log.e(TAG, "Sensor pipeline error", e)
                }
                .collect { data ->
                    processImpact(data)
                    processShake(data)
                    processDrop(data)
                }
        }

        // 收集 EventBus 事件并交由 BehaviorController 处理
        lifecycleScope.launch {
            EventBus.events
                .collect { event ->
                    behaviorController.handleEvent(event)
                }
        }
    }

    private fun processImpact(data: com.example.pat.sensor.AccelData) {
        when (val result = impactDetector.process(data)) {
            is ImpactResult.Detected -> {
                val normalized = ((result.intensity - 25f) / 50f).coerceIn(0f, 1f)
                EventBus.tryEmit(MotionEvent.Impact(normalized))
            }
            is ImpactResult.None -> { /* 无事件 */ }
        }
    }

    private fun processShake(data: com.example.pat.sensor.AccelData) {
        if (shakeDetector.process(data)) {
            EventBus.tryEmit(MotionEvent.Shake)
        }
    }

    private fun processDrop(data: com.example.pat.sensor.AccelData) {
        when (val result = dropDetector.process(data)) {
            is DropResult.Detected -> {
                EventBus.tryEmit(MotionEvent.Drop(result.impactForce))
            }
            is DropResult.None -> { /* 无事件 */ }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 兜底释放（正常情况下 Flow 取消时会自动注销）
        if (::sensorManager.isInitialized) {
            sensorManager.release()
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
