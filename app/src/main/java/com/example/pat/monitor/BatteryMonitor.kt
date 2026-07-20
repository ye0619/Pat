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
 * - [DeviceEvent.LowBattery]：电量低（系统阈值，通常 ~15%）
 * - [DeviceEvent.BatteryFull]：充电完成（电量 ≥100%）
 *
 * 使用 MutableSharedFlow + 动态 BroadcastReceiver，
 * 生命周期通过 [start] / [stop] 管理。
 *
 * 参考文档：5.3 电池监控设计
 */
class BatteryMonitor(
    private val context: Context
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

    override fun start() {
        if (isStarted) return
        isStarted = true
        hasEmittedFull = false

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
        }

        val batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_POWER_CONNECTED -> {
                        isCharging = true
                        hasEmittedFull = false
                        _events.tryEmit(DeviceEvent.ChargeStart)
                        Log.d(TAG, "Event: ChargeStart")
                    }

                    Intent.ACTION_BATTERY_LOW -> {
                        _events.tryEmit(DeviceEvent.LowBattery)
                        Log.d(TAG, "Event: LowBattery")
                    }

                    Intent.ACTION_BATTERY_CHANGED -> {
                        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                        val pct = if (scale > 0) (level * 100 / scale) else -1

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
        Log.i(TAG, "BatteryMonitor started")
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
