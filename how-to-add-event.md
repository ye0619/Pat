# MotionPet — How to Add a New Monitor / Event

> 面向 AI 和开发者的扩展指南。阅读本文档后，你可以为 MotionPet 添加新的设备监测项和反馈事件。

---

## 架构速览

向系统中添加一个新事件的完整数据流：

```
┌─────────────────────────────────────────────────────────────┐
│                     数据来源层                               │
│  Monitor / BroadcastReceiver / Sensor / System API          │
│  负责检测设备状态变化，发射 DeviceEvent                        │
└───────────────────────┬─────────────────────────────────────┘
                        │ DeviceEvent
                        ▼
┌─────────────────────────────────────────────────────────────┐
│                     事件总线层                               │
│  EventBus.tryEmit(event)                                    │
│  所有 Monitor 向此总线写入，所有消费者从此总线读取             │
└───────────────────────┬─────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────┐
│                     分发与规则层                             │
│  EventDispatcher → RuleEngine.mapToEventType()               │
│  将 DeviceEvent 映射为 EventType，匹配用户配置               │
└───────────────────────┬─────────────────────────────────────┘
                        │ EventConfig
                        ▼
┌─────────────────────────────────────────────────────────────┐
│                     反馈层                                   │
│  ResponseManager → Notification / Voice / Vibration         │
└─────────────────────────────────────────────────────────────┘
```

添加新事件 = 在以上每一层增加对新事件类型的支持。

---

## 场景分类

根据新事件的数据来源，选择对应的添加方式：

| 数据来源 | 需要做的事 | 复杂度 | 示例 |
|----------|-----------|--------|------|
| **已有 Monitor 产生的新事件** | 新增 DeviceEvent + EventType + 配置 | 低 | 在 BatteryMonitor 中加入 CHARGE_END |
| **系统广播（Broadcast）** | 新建 Monitor + 以上全部 | 中 | 网络状态变化、时区变化 |
| **传感器数据** | 新建 Detector + 以上全部 | 中 | 翻转手机、敲击 |
| **定时/周期性事件** | 在 Service 中增加 Job + 以上全部 | 低 | 整点报时、每日问候 |

---

## 完整步骤清单

无论哪种场景，最终都要完成以下修改。按文件逐一列出，带有具体代码示例。

### 修改清单

| # | 文件 | 操作 | 必须？ |
|---|------|------|--------|
| 1 | `event/EventType.kt` | 新增枚举值 | ✅ |
| 2 | `event/DeviceEvent.kt` | 新增 sealed class 子类 | ✅ |
| 3 | `config/EventConfig.kt` | 新增默认阈值 + 默认文本 + 中文名 | ✅ |
| 4 | `engine/RuleEngine.kt` | 在 `mapToEventType()` 中添加映射 | ✅ |
| 5 | `engine/RuleEngine.kt` | 在 `checkThreshold()` 中添加阈值逻辑 | 如需阈值 |
| 6 | `ui/EventListScreen.kt` | 在 `EventConfigCard` 中添加阈值显示 | 如需阈值 |
| 7 | `ui/EditEventScreen.kt` | 在 `showThreshold` 中添加滑块范围 | 如需阈值 |
| 8 | Monitor / Service | 产生并发射事件到 EventBus | ✅ |

---

## 步骤详解（以添加 `CHARGE_END` 事件为例）

假想要添加一个"拔掉充电器"的事件，触发文本为"你要带我去哪？"。

### Step 1 — `event/EventType.kt`

在枚举中添加新值：

```kotlin
enum class EventType {
    SCREEN_LONG_USAGE,
    CHARGE_START,
    CHARGE_END,      // ← 新增：结束充电
    LOW_BATTERY,
    SHAKE
}
```

> **规则**：枚举值使用 `UPPER_SNAKE_CASE`，注释写中文说明。

---

### Step 2 — `event/DeviceEvent.kt`

在 sealed class 中添加新子类：

```kotlin
sealed class DeviceEvent {
    // ... 已有事件 ...

    /** 开始充电 */
    data object ChargeStart : DeviceEvent()

    /** 结束充电（拔掉充电器） */            // ← 新增
    data object ChargeEnd : DeviceEvent()   // ← 新增

    // ... 其他事件 ...
}
```

> **选择 data object 还是 data class**：
> - 事件不需要携带参数 → `data object`（如 ChargeStart、Shake）
> - 事件需要携带参数 → `data class`（如 `LongUsage(val minutes: Int)`）

---

### Step 3 — `config/EventConfig.kt`

在 `companion object` 的三个方法中各添加一个分支：

```kotlin
companion object {

    /** 默认阈值，不需要阈值的事件返回 0 */
    fun defaultThreshold(type: EventType): Int = when (type) {
        EventType.SCREEN_LONG_USAGE -> 120
        EventType.LOW_BATTERY -> 20
        EventType.CHARGE_START -> 0
        EventType.CHARGE_END -> 0     // ← 新增：无阈值
        EventType.SHAKE -> 0
    }

    /** 默认反馈文本 */
    fun defaultText(type: EventType): String = when (type) {
        EventType.SCREEN_LONG_USAGE -> "别看了，我想睡觉了"
        EventType.CHARGE_START -> "谢谢给我补充能量"
        EventType.CHARGE_END -> "你要带我去哪？"   // ← 新增
        EventType.LOW_BATTERY -> "我要没电啦"
        EventType.SHAKE -> "别摇我"
    }

    /** UI 展示用中文名 */
    fun displayName(type: EventType): String = when (type) {
        EventType.SCREEN_LONG_USAGE -> "屏幕使用过久"
        EventType.CHARGE_START -> "开始充电"
        EventType.CHARGE_END -> "结束充电"        // ← 新增
        EventType.LOW_BATTERY -> "电量低"
        EventType.SHAKE -> "摇晃手机"
    }
}
```

> **规则**：以上三个 `when` 必须穷举所有 `EventType`。Kotlin 编译器会检查 `when` 是否覆盖所有枚举值——如果遗漏，编译直接报错，不会遗漏。

---

### Step 4 — `engine/RuleEngine.kt`

在 `mapToEventType()` 中添加 DeviceEvent → EventType 映射：

```kotlin
private fun mapToEventType(event: DeviceEvent): EventType? {
    return when (event) {
        is DeviceEvent.LongUsage -> EventType.SCREEN_LONG_USAGE
        is DeviceEvent.ChargeStart -> EventType.CHARGE_START
        is DeviceEvent.ChargeEnd -> EventType.CHARGE_END    // ← 新增
        is DeviceEvent.LowBattery -> EventType.LOW_BATTERY
        is DeviceEvent.Shake -> EventType.SHAKE
        // 不产生反馈的事件 → 返回 null
        is DeviceEvent.Impact,
        is DeviceEvent.Drop,
        is DeviceEvent.ScreenWake,
        is DeviceEvent.ScreenOff,
        is DeviceEvent.BatteryFull,
        is DeviceEvent.LateNight -> null
    }
}
```

> **规则**：
> - 需要用户可配置反馈的事件 → 映射到对应的 `EventType`
> - 系统内部事件、不需要用户配置的事件 → 返回 `null`，不会触发反馈

---

### Step 5 — `engine/RuleEngine.kt`（阈值逻辑，按需）

如果新事件**没有阈值**（如 CHARGE_END），在 `checkThreshold()` 中不需要额外处理——只要在 `mapToEventType` 中正确映射即可，`EventConfig` 的 `enabled` 检查会自动生效。

如果新事件**有阈值**（如屏幕使用时长的分钟数），需要在 `checkThreshold()` 中添加判断：

```kotlin
private fun checkThreshold(event: DeviceEvent, config: EventConfig): Boolean {
    return when (config.eventType) {
        EventType.SCREEN_LONG_USAGE -> {
            if (event is DeviceEvent.LongUsage) event.minutes >= config.threshold
            else true
        }
        EventType.LOW_BATTERY -> {
            true // 系统低电量广播已触发，阈值由 BatteryMonitor 保证
        }
        // 无阈值的事件
        EventType.CHARGE_START,
        EventType.CHARGE_END,   // ← 新增
        EventType.SHAKE -> true
    }
}
```

> **当阈值逻辑复杂时**：如果阈值检查点与事件参数强相关，建议在 Monitor 层完成阈值判断后再发射事件，RuleEngine 中直接返回 `true`。例如 `LOW_BATTERY` 的阈值已在 `BatteryMonitor` 中通过系统广播保证。

---

### Step 6 — `ui/EventListScreen.kt`（阈值显示，按需）

在 `EventConfigCard` 中，为有阈值的事件添加阈值显示行。当前逻辑位于 `EventConfigCard` 内：

```kotlin
// 显示阈值（如有）
if (config.threshold > 0 && config.eventType == EventType.SCREEN_LONG_USAGE) {
    Text("阈值: ${config.threshold}分钟", ...)
}
if (config.threshold > 0 && config.eventType == EventType.LOW_BATTERY) {
    Text("阈值: ${config.threshold}%", ...)
}
```

如果你的新事件有阈值，在这里新增一个 if 块，使用恰当的单位后缀。

---

### Step 7 — `ui/EditEventScreen.kt`（阈值滑块，按需）

如果你的新事件需要可调节的阈值滑块，在 `EditEventScreen` 中修改两处：

**a) 滑块可见性判断：**

```kotlin
val showThreshold = config.eventType == EventType.SCREEN_LONG_USAGE
        || config.eventType == EventType.LOW_BATTERY
        || config.eventType == EventType.YOUR_NEW_TYPE  // ← 新增
```

**b) 滑块范围：**

```kotlin
val sliderRange = when (config.eventType) {
    EventType.SCREEN_LONG_USAGE -> 30f..300f
    EventType.LOW_BATTERY -> 5f..50f
    EventType.YOUR_NEW_TYPE -> 10f..100f  // ← 新增
    else -> 0f..100f
}

val thresholdLabel = when (config.eventType) {
    EventType.SCREEN_LONG_USAGE -> "分钟"
    EventType.LOW_BATTERY -> "%"
    EventType.YOUR_NEW_TYPE -> "次"      // ← 新增
    else -> ""
}
```

---

### Step 8 — 产生事件（Monitor / Service）

最后，在系统中实际产生这个事件。根据数据来源选择：

#### 场景 A：扩展现有 Monitor

在已有的 Monitor 中添加新的广播 action 检测。例如在 `BatteryMonitor.kt` 中添加 `ACTION_POWER_DISCONNECTED`：

```kotlin
// 在 start() 方法的 BroadcastReceiver 中添加：
Intent.ACTION_POWER_DISCONNECTED -> {
    isCharging = false
    _events.tryEmit(DeviceEvent.ChargeEnd)
    Log.d(TAG, "Event: ChargeEnd")
}

// 在 IntentFilter 中添加：
addAction(Intent.ACTION_POWER_DISCONNECTED)
```

#### 场景 B：新建 Monitor

如果你的新事件需要全新的数据源（例如网络状态、蓝牙连接等），实现 `Monitor<T>` 接口：

```kotlin
package com.example.pat.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.example.pat.event.DeviceEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class NetworkMonitor(
    private val context: Context
) : Monitor<DeviceEvent> {

    private val _events = MutableSharedFlow<DeviceEvent>(
        replay = 0,
        extraBufferCapacity = 4
    )
    override val events: SharedFlow<DeviceEvent> = _events.asSharedFlow()

    private var isStarted = false
    private var receiver: BroadcastReceiver? = null

    override fun start() {
        if (isStarted) return
        isStarted = true

        val networkReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                // 处理系统广播，发射 DeviceEvent
                _events.tryEmit(DeviceEvent.NetworkDisconnected)
                Log.d(TAG, "Event: NetworkDisconnected")
            }
        }

        val filter = IntentFilter().apply {
            addAction("android.net.conn.CONNECTIVITY_CHANGE")
        }
        context.registerReceiver(networkReceiver, filter)
        receiver = networkReceiver
        Log.i(TAG, "NetworkMonitor started")
    }

    override fun stop() {
        if (!isStarted) return
        isStarted = false
        receiver?.let { context.unregisterReceiver(it) }
        receiver = null
    }

    companion object {
        private const val TAG = "NetworkMonitor"
    }
}
```

然后在 `CompanionForegroundService.kt` 中注册：

```kotlin
// 在 onCreate 中初始化
private lateinit var networkMonitor: NetworkMonitor
networkMonitor = NetworkMonitor(this)

// 在 onStartCommand 中启动
networkMonitor.start()

// 在 startDeviceEventForwarding 中转发事件
// DeviceStateMonitor 只管理 Screen + Battery，新 Monitor 需要单独转发
serviceScope.launch {
    networkMonitor.events.collect { event ->
        EventBus.tryEmit(event)
    }
}

// 在 onDestroy 中停止
networkMonitor.stop()
```

#### 场景 C：周期性定时事件

如果事件不需要 Monitor，只需在 Service 中定时发射：

```kotlin
// 在 CompanionForegroundService.onStartCommand 中
serviceScope.launch {
    while (isActive) {
        delay(60 * 60 * 1000L) // 每小时
        EventBus.tryEmit(DeviceEvent.HourlyGreeting)
    }
}
```

---

## 权限与 Manifest

如果新 Monitor 需要特殊权限，在 `AndroidManifest.xml` 中添加：

```xml
<!-- 示例：网络状态 -->
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

> 注意：普通权限（Normal Permission）仅需 manifest 声明。危险权限（Dangerous Permission）必须在 `MainActivity` 中运行时请求。

---

## 验证清单

添加完成后，按以下步骤验证：

- [ ] `./gradlew assembleDebug` 编译通过
- [ ] 安装到设备，Service 正常启动
- [ ] 触发条件满足时，通知正确弹出
- [ ] 配置界面中可见新事件类型，可开关
- [ ] 可编辑反馈文本，保存后生效
- [ ] （如有阈值）阈值滑块可调节
- [ ] （如有语音）可上传并播放音频文件
- [ ] 冷却机制正常（同一事件 10 分钟内不重复触发）

---

## 已修改文件总览

添加一个事件类型始终需要修改这些文件：

```
event/EventType.kt          ← 新枚举值
event/DeviceEvent.kt        ← 新 sealed 子类
config/EventConfig.kt       ← 默认阈值 + 默认文本 + 中文名
engine/RuleEngine.kt        ← mapToEventType + checkThreshold
ui/EventListScreen.kt       ← 阈值显示行（按需）
ui/EditEventScreen.kt       ← 滑块范围（按需）
+ Monitor / Service         ← 产生事件
```

`EventConfig.kt` 中的 `defaultThreshold`、`defaultText`、`displayName` 三个方法使用 Kotlin `when` 穷举枚举值——编译器会检查遗漏，无需担心忘记某个分支。

---

## 快速参考：各事件类型的阈值设计

| 事件 | 阈值含义 | 单位 | 滑块范围 |
|------|---------|------|---------|
| SCREEN_LONG_USAGE | 累计亮屏时间 | 分钟 | 30–300 |
| LOW_BATTERY | 电量百分比 | % | 5–50 |
| 无阈值事件 | 始终返回 0 | — | 不显示滑块 |

---

## 注意事项

1. **不要在 Receiver 中写业务逻辑**。BroadcastReceiver 的方法运行在主线程，只负责将系统 Intent 转换为 DeviceEvent 并发射。
2. **EventBus.tryEmit 是线程安全的**。可以在 BroadcastReceiver（主线程）、传感器回调（后台线程）、协程中任意调用。
3. **冷却机制是全局的**。EventDispatcher 对每个 EventType 有 10 分钟冷却，如需调整修改 `EventDispatcher.cooldownMs`。
4. **首次安装默认启用所有事件**。PreferenceManager 的 `createDefaults()` 会为所有 `EventType.entries` 创建默认配置。
