<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp" alt="Pat" width="120" height="120" />
</p>

<h1 align="center">Pat</h1>

<p align="center">
  <em>一只住在你手机里的电子宠物 — 会抱怨、会道谢，也会喊疼。</em>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/minSdk-26-0066CC?style=flat-square&logo=android" alt="minSdk 26" />
  <img src="https://img.shields.io/badge/targetSdk-36-0066CC?style=flat-square&logo=android" alt="targetSdk 36" />
  <img src="https://img.shields.io/badge/language-Kotlin-7F52FF?style=flat-square&logo=kotlin" alt="Kotlin" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=flat-square&logo=jetpackcompose" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/license-MIT-green?style=flat-square" alt="License" />
</p>

---

## 📖 简介

**Pat** 是一款基于传感器的事件驱动型 Android 电子宠物应用。它持续感知你的手机状态 — 屏幕使用、电池电量、摇晃、跌落、敲击 — 并在合适的时机用语音和通知给你拟人化的反馈。

| 事件 | 它会说… |
|------|---------|
| 📱 屏幕使用太久 | "别看了，我想睡觉了" |
| 🔌 插上充电器 | "谢谢给我补充能量" |
| 🪫 电量过低 | "我要没电啦" |
| 🤚 摇晃手机 | "别摇我" |
| 💥 手机摔落 | "好痛！我摔到了" |

> 你也可以创建自己的**自定义事件**，设定触发条件和反馈内容，让它更懂你。

---

## ✨ 功能

- **5 种内置预设事件** — 长时间使用、开始充电、电量低、摇晃、跌落
- **自定义事件系统** — 支持摇晃、敲击、跌落、亮屏/息屏、充电/断电、电量变化、深夜使用、点击、长按等 13 种原子事件类型
- **条件引擎** — 滑动时间窗口 + 数值比较 + 次数门槛 + 跨午夜时间段检测
- **反应池** — 每个事件可配置多条文字+语音反馈，触发时随机播放
- **语音合成** — 内置语音由 [GPT-SoVITS](https://github.com/RVC-Boss/GPT-SoVITS) 合成
- **通知支持** — 横幅通知、提示音、震动、锁屏可见性，每个事件独立配置
- **冷却机制** — 防止同一事件短时间重复触发
- **开机自启** — 重启手机后自动恢复后台服务
- **浅色/深色主题** — 支持跟随系统 + 手动切换
- **冲突检测** — 自定义事件传感器冲突自动提醒

---

## 🛠 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin |
| UI | Jetpack Compose + Material3 |
| 架构 | 单 Activity + 密封类导航 |
| 后台服务 | Foreground Service |
| 传感器 | SensorManager (加速度计, `SENSOR_DELAY_GAME`) |
| 异步 | Kotlin Coroutines + SharedFlow 事件总线 |
| 持久化 | SharedPreferences + JSON |
| 音频 | MediaPlayer + AudioManager |
| 设计 | Apple 风格设计语言 |
| 构建 | Gradle Kotlin DSL + Version Catalog |

---

## 📦 构建

```bash
# 克隆仓库
git clone https://github.com/ye0619/Pat.git
cd Pat

# 使用 Android Studio 打开项目目录，或命令行构建：
./gradlew assembleDebug
```

| 配置 | 值 |
|------|-----|
| `compileSdk` | 36 |
| `minSdk` | 26 |
| `targetSdk` | 36 |
| JDK | 11+ |

---

## 🧩 项目结构

```
app/src/main/java/com/example/pat/
├── MainActivity.kt              # 单 Activity 入口
├── navigation/
│   └── Screen.kt                # 密封类导航定义
├── ui/
│   ├── HomeScreen.kt            # 主仪表盘
│   ├── EventListScreen.kt       # 事件列表
│   ├── EditEventScreen.kt       # 预设事件编辑
│   ├── NewEventScreen.kt        # 自定义事件编辑
│   ├── PresetEditScreen.kt      # 反馈预设编辑
│   ├── ClickTracker.kt          # 点击/长按事件桥接
│   ├── theme/                   # Apple 风格主题 (Color/Type/Shape/Theme)
│   └── components/              # 公共组件 (EventCard/StatusIndicator/ActionButton)
├── model/                       # 数据模型 (EventConfig/EventDefinition/ReactionPreset)
├── event/                       # 事件系统 (AtomicEvent/AtomicEventBus/EventType)
├── engine/                      # 规则引擎 (RuleEngineV2/ConditionEvaluator/PriorityResolver)
├── detector/                    # 传感器检测器 (ShakeDetector/DropDetector/ImpactDetector)
├── sensor/                      # 传感器管理 (MotionSensorManager)
├── monitor/                     # 状态监控 (ScreenMonitor/BatteryMonitor/DeviceStateMonitor)
├── service/                     # 前台服务 + 开机广播
├── response/                    # 反馈管理 (ResponseManager/NotificationService/VoiceService)
├── audio/                       # 音频播放 (AudioPlayer/AudioPlaybackState)
├── data/                        # 数据仓库 (JSON 序列化/反序列化)
└── preset/                      # 预设加载器
```

---

## 🙏 鸣谢

- **[taigrr/spank](https://github.com/taigrr/spank)** — 项目灵感来源
- **[ChatGPT](https://chatgpt.com)** — 应用图标生成
- **[GPT-SoVITS](https://github.com/RVC-Boss/GPT-SoVITS)** — 语音合成
- **[DeepSeek](https://deepseek.com)** — 代码编写
- **楼先生** — 对本项目的大力支持

---

## ⚠️ 免责声明

没有任何人在该项目中受到伤害。

---

## 📄 许可证

MIT License

---

<p align="center">
  <sub>Made with ❤️ by <a href="https://github.com/ye0619">ye0619</a></sub>
</p>
