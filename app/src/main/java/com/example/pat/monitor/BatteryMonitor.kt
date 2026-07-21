package com.example.pat.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import com.example.pat.event.DeviceEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 电池状态监控器。
 *
 * 监听系统电池广播，产生以下事件：
 * - [DeviceEvent.ChargeStart]：连接充电器
 * - [DeviceEvent.LowBattery]：电量低于自定义阈值（默认 20%），或触发系统低电量广播
 * - [DeviceEvent.BatteryFull]：充电完成（电量 ≥100%）
 *
 * 低电量检测策略：
 * - 优先使用自定义阈值 [lowBatteryPercent]（通过 [ACTION_BATTERY_CHANGED] 实时检测）
 * - 同时保留系统 [Intent.ACTION_BATTERY_LOW] 作为兜底
 * - 带滞回机制：触发后需回升到 阈值+5% 以上才解除锁定
 *
 * 使用 MutableSharedFlow + 动态 BroadcastReceiver，
 * 生命周期通过 [start] / [stop] 管理。
 *
 * 参考文档：5.3 电池监控设计
 */
class BatteryMonitor(
    private val context: Context,
    /** 低电量自定义阈值（百分比，0 表示仅使用系统默认阈值） */
    private var lowBatteryPercent: Int = 20
) : Monitor<DeviceEvent> {

    private val _events = MutableSharedFlow<DeviceEvent>(
        replay = 0,
        extraBufferCapacity = 4
    )
    override val events: SharedFlow<DeviceEvent> = _events.asSharedFlow()

    private var isStarted = false
    private var receiver: BroadcastReceiver? = null
    private var isCharging = false
    private var hasEmittedFull = false
    /** 低电量已触发标记（滞回：需回升到阈值以上才解除） */
    private var lowBatteryEmitted = false

    override fun start() {
        if (isStarted) return
        isStarted = true
        hasEmittedFull = false
        lowBatteryEmitted = false

        // 查询初始充电状态
        val stickyIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        if (stickyIntent != null) {
            val status = stickyIntent.getIntExtra(
                BatteryManager.EXTRA_STATUS,
                BatteryManager.BATTERY_STATUS_UNKNOWN
            )
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL

            // 初始电量检测：如果启动时已低于阈值，立即触发
            if (!isCharging) {
                val level = stickyIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = stickyIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val pct = if (scale > 0) (level * 100 / scale) else -1
                if (pct in 1..lowBatteryPercent) {
                    lowBatteryEmitted = true
                    _events.tryEmit(DeviceEvent.LowBattery)
                    Log.d(TAG, "Event (init): LowBattery ($pct%)")
                }
            }
        }

        val batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_POWER_CONNECTED -> {
                        isCharging = true
                        hasEmittedFull = false
                        // 充电开始 → 重置低电量标记（电量将回升）
                        lowBatteryEmitted = false
                        _events.tryEmit(DeviceEvent.ChargeStart)
                        Log.d(TAG, "Event: ChargeStart")
                    }

                    Intent.ACTION_BATTERY_LOW -> {
                        // 系统低电量广播作为兜底（通常 ~15%）
                        // 如果自定义阈值已触发过，跳过
                        if (lowBatteryEmitted) {
                            Log.d(TAG, "LowBattery system broadcast skipped (already emitted via custom threshold)")
                            return
                        }
                        lowBatteryEmitted = true
                        _events.tryEmit(DeviceEvent.LowBattery)
                        Log.d(TAG, "Event: LowBattery (system)")
                    }

                    Intent.ACTION_BATTERY_CHANGED -> {
                        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                        val pct = if (scale > 0) (level * 100 / scale) else -1
                        val status = intent.getIntExtra(
                            BatteryManager.EXTRA_STATUS,
                            BatteryManager.BATTERY_STATUS_UNKNOWN
                        )

                        // 更新充电状态
                        val currentlyCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                                || status == BatteryManager.BATTERY_STATUS_FULL

                        // ── 自定义阈值低电量检测（带滞回） ──
                        if (lowBatteryPercent > 0 && !currentlyCharging) {
                            val recoveryThreshold = (lowBatteryPercent + 5).coerceAtMost(100)

                            if (!lowBatteryEmitted && pct in 1..lowBatteryPercent) {
                                // 电量降到阈值以下 → 触发
                                lowBatteryEmitted = true
                                _events.tryEmit(DeviceEvent.LowBattery)
                                Log.d(TAG, "Event: LowBattery ($pct% <= ${lowBatteryPercent}%)")
                            } else if (lowBatteryEmitted && pct > recoveryThreshold) {
                                // 电量回升到恢复阈值以上 → 解除锁定
                                lowBatteryEmitted = false
                                Log.d(TAG, "LowBattery lock released ($pct% > $recoveryThreshold%)")
                            }
                        }

                        // 更新 isCharging（用于 BatteryFull 判断）
                        if (currentlyCharging != isCharging) {
                            isCharging = currentlyCharging
                        }

                        // ── 充电完成检测 ──
                        if (isCharging && pct >= 100 && !hasEmittedFull) {
                            hasEmittedFull = true
                            _events.tryEmit(DeviceEvent.BatteryFull)
                            Log.d(TAG, "Event: BatteryFull")
                        }
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        context.registerReceiver(batteryReceiver, filter)
        receiver = batteryReceiver
        Log.i(TAG, "BatteryMonitor started (lowBatteryThreshold=${lowBatteryPercent}%)")
    }

    /**
     * 更新低电量阈值（百分比）。
     * 可在运行时动态调整，会重置滞回状态。
     */
    fun updateLowBatteryThreshold(percent: Int) {
        if (percent != lowBatteryPercent) {
            Log.i(TAG, "LowBattery threshold updated: $lowBatteryPercent% → $percent%")
            lowBatteryPercent = percent
            // 阈值变化时重置标记，允许新阈值下重新触发
            lowBatteryEmitted = false
        }
    }

    override fun stop() {
        if (!isStarted) return
        isStarted = false
        receiver?.let { context.unregisterReceiver(it) }
        receiver = null
        Log.i(TAG, "BatteryMonitor stopped")
    }

    companion object {
        private const val TAG = "BatteryMonitor"
    }
}
