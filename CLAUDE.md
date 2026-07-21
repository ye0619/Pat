# Pat (MotionPet) — Android 互动伴侣应用

## 项目概述

Pat 是一个 Android 前台服务应用，持续监测设备状态（传感器、电量、屏幕），根据用户配置的事件规则触发通知、语音和音频反馈。

**技术栈**: Kotlin, Jetpack Compose (Material 3), Coroutines + SharedFlow, SharedPreferences JSON 持久化

**构建配置**:
- compileSdk: 36, minSdk: 26, targetSdk: 36
- Kotlin 2.2.10, AGP 9.1.1, compose-bom:2024.09.00
- 无 DI 框架, 无 Room, 无 DataStore

---

## 目录结构

```
app/src/main/java/com/example/pat/
├── audio/
│   ├── AudioPlayer.kt          # 播放 assets/ 中的内置音频
│   └── AudioPlaybackState.kt   # 全局播放状态（单例，通知栏+首页播放器）
├── data/
│   ├── EventConfigRepository.kt # EventConfig 持久化 (SP + JSON)
│   ├── PresetRepository.kt     # ReactionPreset 管理（内置+自定义）
│   └── UserRuleRepository.kt   # UserRule 持久化 (SP + JSON)
├── detector/
│   ├── MotionDetector.kt       # 检测器接口 process(AccelData): T
│   ├── ShakeDetector.kt        # 摇晃检测
│   ├── ImpactDetector.kt       # 拍击检测
│   └── DropDetector.kt         # 跌落检测
├── engine/
│   ├── ConditionEvaluator.kt   # 条件评估器（AND/OR/SEQUENCE/时间段）
│   ├── EventDispatcher.kt      # 收集 EventBus → RuleEngine → ResponseManager
│   ├── EventHistoryBuffer.kt   # 滑动时间窗口缓冲区
│   ├── RuleEngine.kt           # 旧规则引擎（EventConfig 1:1 映射）
│   └── RuleEngineV2.kt         # 新规则引擎（UserRule 组合条件）
├── event/
│   ├── AtomicEvent.kt          # 原子事件 sealed class（10+ 子类）
│   ├── AtomicEventBus.kt       # 原子事件总线
│   ├── AtomicEventType.kt      # 原子事件类型枚举
│   ├── DeviceEvent.kt          # 语义事件 sealed class（旧）
│   ├── DeviceEventLogEntry.kt  # 事件日志条目
│   ├── EventBus.kt             # 语义事件总线（旧）
│   ├── EventType.kt            # 基础事件类型枚举（5个）
│   └── SensorDataBus.kt        # 传感器原始数据总线
├── model/
│   ├── ConditionClause.kt      # 条件子句（事件类型+比较符+次数+时间段）
│   ├── EventConfig.kt          # 基础事件配置（1 EventType → 1 规则）
│   ├── ReactionPreset.kt       # 反馈预设（文本+音频）
│   └── UserRule.kt             # 用户自定义规则（多条件组合）
├── monitor/
│   ├── Monitor.kt              # 监控器接口 start/stop/events
│   ├── BatteryMonitor.kt       # 电池监控
│   ├── ScreenMonitor.kt        # 屏幕监控
│   └── DeviceStateMonitor.kt   # 复合监控器
├── preset/
│   └── PresetLoader.kt         # 从 assets/ 扫描 .wav 文件构建预设
├── response/
│   ├── ResponseManager.kt      # 反馈协调（通知+音频）
│   ├── NotificationService.kt  # Heads-up 通知
│   └── VoiceService.kt         # 用户上传音频播放
├── sensor/
│   ├── SensorData.kt           # AccelData + GyroData 数据类
│   ├── SensorCallback.kt       # 传感器回调接口
│   └── MotionSensorManager.kt  # 加速度计生命周期管理
├── service/
│   ├── CompanionForegroundService.kt  # 前台服务（核心入口）
│   ├── CompanionBootReceiver.kt       # 开机自启
│   └── ClickAccessibilityService.kt   # 点击检测（无障碍服务）
├── ui/
│   ├── HomeScreen.kt           # 首页
│   ├── EventListScreen.kt      # 事件管理列表（统一）
│   ├── EditEventScreen.kt      # 基础事件编辑
│   ├── PresetEditScreen.kt     # 预设编辑器
│   ├── RuleBuilderScreen.kt    # 规则构建器
│   └── navigation/Screen.kt    # 导航状态
├── util/
│   └── PermissionManager.kt    # 通知权限管理
└── MainActivity.kt             # 唯一 Activity
```

---

## 核心架构：双事件总线 + 双规则引擎

```
┌──────────────────────────────────────────────────────────┐
│                     传感器 & 系统层                       │
│  MotionSensorManager  BatteryMonitor  ScreenMonitor     │
│  ClickAccessibilityService (可选)                        │
│        │                  │              │               │
│        ├─ SensorDataBus (raw AccelData)                  │
│        ├─ EventBus (DeviceEvent) ──→ EventDispatcher     │
│        │                              └─→ RuleEngine     │
│        │                                   └─→ ResponseManager
│        └─ AtomicEventBus (AtomicEvent) ──→ RuleEngineV2  │
│                                             └─→ executeRuleReaction
└──────────────────────────────────────────────────────────┘
```

**关键设计原则**:
1. 传感器层不感知规则，只产生原子事件
2. 规则引擎不感知传感器实现
3. 新旧两套系统并行运行，互不干扰
4. SharedPreferences + 手动 JSON 序列化（无第三方存储库）

---

## 事件类型体系

### EventType (5 个基础类型 — 旧系统)
```kotlin
enum class EventType {
    SCREEN_LONG_USAGE,  // 阈值=分钟
    CHARGE_START,       // 充电开始
    LOW_BATTERY,        // 阈值=电量%
    SHAKE,              // 摇晃
    IMPACT              // 拍击
}
```

### AtomicEventType (11 个原子类型 — 新系统)
```kotlin
enum class AtomicEventType(displayName, hasValue, supportsCount, supportsTimeRange, requiresAccessibility) {
    SHAKE, IMPACT, DROP,           // 计数事件（加速度计）
    SCREEN_ON, SCREEN_OFF,         // 状态事件（不计数）
    CHARGE_START, CHARGE_STOP,     // 状态事件（不计数）
    BATTERY_LEVEL(hasValue=true),  // 数值事件
    LONG_USAGE(hasValue=true),     // 数值事件
    LATE_NIGHT(supportsTimeRange), // 时间段事件
    CLICK(requiresAccessibility)   // 点击事件（需无障碍服务）
}
```

### AtomicEvent sealed class (11 个子类)
```kotlin
Shake(timestamp) | Impact(timestamp, intensity) | Drop(timestamp, impactForce)
ScreenOn/Off(timestamp) | ChargeStart/Stop(timestamp)
BatteryLevel(timestamp, percent) | LongUsage(timestamp, minutes)
LateNight(timestamp, hour) | Click(timestamp)
```

---

## 检测器参数

| 检测器 | 参数 | 值 | 说明 |
|--------|------|-----|------|
| ShakeDetector | threshold | 13.0 m/s² | 偏离重力的阈值 |
| | countRequired | 7 | 窗口内需要次数 |
| | timeWindow | 700ms | 统计窗口 |
| | cooldown | 2000ms | 两次触发间隔 |
| ImpactDetector | threshold | 18.0 m/s² | 拍击判定 |
| | windowSize | 3 | 滑动窗口 |
| | cooldown | 500ms | 防误触间隔 |
| DropDetector | freeFallThreshold | 2.0 m/s² | 失重判定 |
| | freeFallDuration | 200ms | 最短失重时间 |
| | impactThreshold | 30.0 m/s² | 撞击判定 |
| | globalCooldown | 30s | 全局冷却 |
| AccelData | STILL_LOWER | 7.5 | 静止范围下限 |
| | STILL_UPPER | 12.5 | 静止范围上限 |
| | GRAVITY | 9.80665 | 重力参考值 |

---

## 数据模型

### EventConfig（基础事件配置）
```kotlin
data class EventConfig(
    id, eventType: EventType, enabled, threshold, presetId,
    notificationEnabled, minIntervalMinutes, priority: Int = 5,
    vibrationEnabled = false, soundEnabled = false,
    showHeadsUp = true, lockScreenPublic = true,
    customText = "", customAudioPath = ""
)
```
- 每个 EventType 仅一条配置
- 通过 `presetId` 关联 ReactionPreset 获取文本/音频
- 存储: `SharedPreferences("motionpet_event_rules")` key=`event_configs_v2`

### UserRule（用户自定义规则）
```kotlin
data class UserRule(
    id, name, conditions: List<ConditionClause>,
    operator: ConditionOperator, timeWindowMs, priority,
    enabled, reactionText, reactionAudioPath,
    notificationEnabled, vibrationEnabled, soundEnabled,
    showHeadsUp, lockScreenPublic, minIntervalMinutes,
    conflictStrategy: ConflictStrategy
)
```
- 存储: `SharedPreferences("motionpet_user_rules")` key=`user_rules_v1`

### ConditionClause（条件子句）
```kotlin
data class ConditionClause(
    eventType: AtomicEventType,
    operator: CompareOp?,    // < <= > >= =
    value: Int?,             // 比较阈值
    count: Int = 1,          // 发生次数
    valueMin: Int?,          // 时间段起始小时
    valueMax: Int?           // 时间段结束小时
)
```

### ReactionPreset（反馈预设）
```kotlin
data class ReactionPreset(
    id, name, text, audioAssetPath, audioType: AudioType, eventType: EventType?
)
```
- 内置预设: 从 `assets/*.wav` 扫描（PresetLoader）
- 自定义预设: `SharedPreferences("motionpet_presets")` key=`custom_presets`

---

## 通知渠道

| 渠道 ID | 用途 | Importance | 特点 |
|---------|------|-----------|------|
| `pat_event_alerts_v2` | 事件反馈 | HIGH | 渠道不预设声音/震动，通知级控制 |
| `pat_audio_playback` | 播放状态 | LOW | 静音，仅显示播放信息+停止按钮 |
| `motionpet_service` | 前台服务 | LOW | 常驻通知 |

---

## 音频播放

- `AudioPlayer.playAsset(path)` — 播放 `assets/` 中的 .wav
- `VoiceService.play(path)` — 播放文件系统路径
- `AudioPlaybackState` — 全局单例，跟踪播放状态
  - `isPlaying: StateFlow<Boolean>` — 首页观察
  - `currentName: StateFlow<String>` — 文件名
  - `togglePause()` / `onStop()` — 控制
  - 自动显示播放通知（含停止按钮）

---

## 关键流程

### 基础事件触发流程
```
检测器/Monitor → EventBus.tryEmit(DeviceEvent)
  → EventDispatcher.collect
    → RuleEngine.evaluate() → EventConfig?
    → 冷却检查
    → ResponseManager.execute(config)
      → effectiveText(preset) / effectiveAudioPath(preset)
      → NotificationService.show()
      → AudioPlayer/VoiceService.play()
```

### 自定义规则触发流程
```
检测器/Monitor → AtomicEventBus.tryEmit(AtomicEvent)
  → RuleEngineV2.collect
    → EventHistoryBuffer.record()
    → ConditionEvaluator.evaluate() × N
    → PriorityResolver → 选出最高优先级
    → executeRuleReaction(userRule)
      → NotificationService.show()
      → AudioPlayer/VoiceService.play()
```

### 传感器管道（在 CompanionForegroundService 中）
```
MotionSensorManager (SENSOR_DELAY_GAME, ~50Hz)
  → SensorCallback.onSensorChanged(AccelData)
    → SensorDataBus.tryEmit(data)
    → DropDetector.process(data) → 如果检测到跌落 → 跳过
    → ImpactDetector.process(data)
    → ShakeDetector.process(data)
    → EventBus + AtomicEventBus 双通道发射
```

---

## UI 导航

```
MainActivity (Screen sealed class 状态导航)
├── Home                    → HomeScreen
│     ├── 运行状态 + 今日触发次数 + 最近事件
│     ├── 音频播放器（播放中显示）
│     ├── 通知设置引导（仅首次）
│     ├── 🧪 测试通知（可展开）
│     └── "事件管理" 按钮
├── EventList               → EventListScreen
│     ├── 统一列表（基础事件 + 自定义规则）
│     ├── 📋/🔢 排序切换
│     └── [+] 新建自定义规则
├── EditEvent(eventType)    → EditEventScreen
│     ├── 启用开关 + 阈值滑块
│     ├── 预设选择 + 创建自定义预设
│     ├── 优先级滑块
│     ├── 通知设置（5个开关）
│     └── 冷却时间
├── EditPreset(eventType)   → PresetEditScreen
│     ├── 名称 + 文本 + 音频上传
│     └── 保存后关联到 EventConfig.presetId
├── RuleBuilder(ruleId?)    → RuleBuilderScreen
│     ├── 规则名称 + 条件列表
│     ├── 组合方式 (AND/OR/SEQ)
│     ├── 时间窗口 + 优先级 + 冷却
│     ├── 反馈文本 + 音频（上传/预设）
│     └── 通知设置（5个开关）
└── RuleList                → RuleListScreen (备用)
```

---

## 权限

| 权限 | 类型 | 用途 |
|------|------|------|
| VIBRATE | Normal | 通知震动 |
| POST_NOTIFICATIONS | Runtime (API 33+) | 发送通知 |
| FOREGROUND_SERVICE | Normal | 前台服务 |
| FOREGROUND_SERVICE_SPECIAL_USE | Normal (API 34+) | 前台服务类型 |
| RECEIVE_BOOT_COMPLETED | Normal | 开机自启动 |
| BIND_ACCESSIBILITY_SERVICE | 系统级 | 点击检测（默认关闭） |

---

## 持久化文件

| SharedPreferences 名称 | Key | 内容 |
|----------------------|-----|------|
| `motionpet_event_rules` | `event_configs_v2` | List\<EventConfig\> JSON |
| `motionpet_presets` | `custom_presets` | List\<ReactionPreset\> JSON |
| `motionpet_screen_state` | `total_accumulated_ms`, `long_usage_emitted`, `last_reset_date` | 亮屏计时 |
| `motionpet_user_rules` | `user_rules_v1` | List\<UserRule\> JSON |
| `motionpet_ui_state` | `notification_guide_dismissed` | 通知引导已关闭 |

---

## 开发注意事项

1. **添加新基础事件类型**: 修改 `EventType` 枚举 → `EventConfig.defaultText/defaultThreshold/displayName` → `RuleEngine.mapToEventType()` → `EventConfigRepository.createDefaults()`
2. **添加新原子事件**: 修改 `AtomicEventType` 枚举 → `AtomicEvent` sealed class → `toType()` 映射 → Monitor/Detector 发射
3. **渠道重建**: 修改 `NotificationService.CHANNEL_ID` 值即可强制重建通知渠道
4. **IMPACT 检测调试**: 阈值 18.0 m/s²，窗口 3，静止范围 [7.5, 12.5]。如果无法触发，检查设备是否在手持时产生足够的加速度峰值
5. **无障碍服务**: `ClickAccessibilityService` 默认不启用，用户选择 CLICK 条件时自动跳转系统设置
6. **优先级冲突**: 多个规则同时匹配时，取最高优先级（`ConflictStrategy.HIGHEST_PRIORITY`）
