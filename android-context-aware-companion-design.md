# Android Context-Aware Companion — Architecture Design Document

> A background-resident Android application that observes device and user behavior to produce personality-driven, lightweight feedback. The phone is not a tool — it has a presence.

---

## Table of Contents

1. [Project Concept](#1-project-concept)
2. [Product Design Principles](#2-product-design-principles)
3. [Android System Architecture](#3-android-system-architecture)
4. [Background Runtime Architecture](#4-background-runtime-architecture)
5. [Device Behavior Monitoring System](#5-device-behavior-monitoring-system)
6. [Event System Design](#6-event-system-design)
7. [Personality Engine (Core)](#7-personality-engine-core)
8. [Response System Design](#8-response-system-design)
9. [Recommended Project Structure](#9-recommended-project-structure)
10. [Data Model Design](#10-data-model-design)
11. [MVP Development Roadmap](#11-mvp-development-roadmap)
12. [AI Extension Path](#12-ai-extension-path)
13. [Performance & Privacy Design](#13-performance--privacy-design)
14. [Project Highlights](#14-project-highlights)

---

## 1. Project Concept

### 1.1 Core Idea

This is not an app the user opens. It is a background process that listens — to the device being picked up, to the battery dying, to a notification arriving late at night — and reacts with a personhood that makes the phone feel less like a tool and more like a presence.

The application does not request attention. It responds to context. The user does not operate a character — the character observes the user and reacts.

```
User picks up phone at 2 AM
  → Screen亮了
    → "你还不睡啊… 我都要没电了."
       (Notification + gentle vibration)
```

### 1.2 Why Background, Not Foreground

| Aspect | Traditional Companion App | This Project |
|--------|--------------------------|--------------|
| Entry point | User taps icon | System event triggers response |
| Interaction model | User → UI → Character | Device state → Character → User |
| Engagement | Requires active sessions | Momentary, ambient |
| User expectation | "I'm playing a game" | "My phone has moods" |
| Burnout risk | High (competitive, task-based) | Low (passive, surprising) |

A companion that waits to be opened is a game. A companion that speaks when it notices something is a presence. Background delivery is not a technical compromise — it is the defining product decision.

### 1.3 Differentiators from Virtual Pet Apps

- **No open-loop lifecycle**: The user never "feeds" or "cleans" the character. Feedback is event-driven, not schedule-driven.
- **No UI dependency**: The app can be reduced to a notification channel + sound set. The screen is optional.
- **Reactive, not interactive**: The character acts on observations, not commands. This inverts the traditional interaction graph.
- **System-level awareness**: The character knows about battery, screen state, charging, motion — things a game character cannot sense.

---

## 2. Product Design Principles

### 2.1 Minimalism

- **One screen max**: Settings, if any, are a single-page preference panel. No dashboard, no stats, no history.
- **Zero onboarding**: First install → foreground service starts → first event triggers first response. No account, no tutorial, no avatar picker.
- **Sparse configuration**: At most 3-5 knobs (character choice, notification sensitivity, quiet hours). Defaults should be good enough for 80% of users.

### 2.2 Passive Interaction

The user never needs to "check" on the character. All interaction is initiated by the system:

- System event detected → character responds → user may or may not notice
- No "tap to pet" mechanic. No swipe gestures. No daily missions.
- If a user opens the app, they should see only a log of recent events with the character's reactions — a read-only history, not a dashboard.

### 2.3 Strong Feedback from Sparse Events

Every response must carry weight:

- No repeating the same notification twice in a row without context change.
- Rate-limiting is a product feature, not just a technical constraint: if nothing happens, the character stays quiet. This builds anticipation.
- Feedback variety: the same event type can produce text, sound, vibration, or silence — chosen probabilistically per personality.

### 2.4 Personified Consistency

- The character has a name, a voice style, and a mood that shifts based on event history.
- Two characters experiencing the same event must produce distinct responses (different wording, different timing, different channel).
- Mood is not a game mechanic — it is a filter over response selection. A "tired" character uses shorter messages and prefers vibration over sound.

---

## 3. Android System Architecture

### 3.1 Layered Architecture

```
┌────────────────────────────────────────────────────────────┐
│                     Android System                          │
│  (BroadcastReceiver, SensorManager, PowerManager, etc.)     │
└────────────────────────┬───────────────────────────────────┘
                         │ raw system signals
                         ▼
┌────────────────────────────────────────────────────────────┐
│                   Monitor Layer                             │
│  BatteryMonitor  ScreenMonitor  SensorMonitor  TimeMonitor  │
│  Listens → normalizes → emits typed DeviceEvent             │
└────────────────────────┬───────────────────────────────────┘
                         │ DeviceEvent
                         ▼
┌────────────────────────────────────────────────────────────┐
│                    Event Layer                              │
│  EventDispatcher  →  EventQueue  →  EventFilter             │
│  Rate-limiting, deduplication, quiet-hours                  │
└────────────────────────┬───────────────────────────────────┘
                         │ filtered Event
                         ▼
┌────────────────────────────────────────────────────────────┐
│                 Personality Engine                          │
│  Character  →  Mood  →  ResponseSelector                    │
│  Maps Event + Character to ResponseIntent                   │
└────────────────────────┬───────────────────────────────────┘
                         │ ResponseIntent
                         ▼
┌────────────────────────────────────────────────────────────┐
│                   Response Layer                            │
│  NotificationService  SoundService  VibrationService        │
│  Executes the response on the correct channel               │
└────────────────────────────────────────────────────────────┘
```

### 3.2 Layer Responsibilities

| Layer | Input | Output | Extensibility |
|-------|-------|--------|---------------|
| Monitor | System APIs / Broadcasts / Sensors | `DeviceEvent` sealed class | Add a new `Monitor<T>` implementation for any new system signal |
| Event | `DeviceEvent` stream | Filtered `DeviceEvent` | Add event post-processing (dedup strategies, cooldown rules) |
| Personality Engine | `DeviceEvent` + current state | `ResponseIntent` | Swap character profile, add mood model, plug in LLM |
| Response | `ResponseIntent` | User-visible feedback (Notification / Sound / Vibration) | Add new output channel (e.g. LED blink, smart home trigger) |

### 3.3 Dependency Direction

```
Monitor → Event → Personality → Response
```

Monitor never knows about personality. Event never knows about response. Personality never knows about Android channels.

This is the critical constraint. Violating it merges concerns and makes testing impossible.

---

## 4. Background Runtime Architecture

### 4.1 Foreground Service

**Why it is required (Android 8+ / API 26+):**

Background execution is severely restricted starting from Android 8 (API 26). A regular `Service` will be killed within minutes of the app entering the background. A `ForegroundService` with a persistent notification is the only reliable way to maintain a long-lived process on modern Android.

**Usage:**

- The service is started at `onBootCompleted` (via `BroadcastReceiver`) and on app install.
- It displays a low-priority notification (e.g. "Companion is running") that the user can hide on supported Android versions (Android 13+ allows dismissing FG notifications).
- The service holds the `Monitor` layer lifecycle — when the service is destroyed, monitoring stops.

**Lifecycle diagram:**

```
Boot / Install
    │
    ▼
onStartCommand
    │
    ├── startForeground(notification)
    ├── initialize monitors
    ├── start personality engine
    └── return START_STICKY
    │
    ▼
onDestroy
    │
    ├── release monitors
    ├── persist mood/state
    └── stopForeground(REMOVE)
```

**START_STICKY** is critical. If the system kills the service for memory pressure, `START_STICKY` guarantees it will be recreated when resources allow.

### 4.2 Broadcast Receiver

Used for **instantaneous, system-broadcast events**. Registration is done in `AndroidManifest.xml` (manifest-declared receivers work on Android 8+ only for specific broadcasts; dynamic registration via `registerReceiver` in the foreground service covers the rest).

| Broadcast | Action | Registration |
|-----------|--------|-------------|
| Battery low / ok | `ACTION_BATTERY_LOW` / `ACTION_BATTERY_OKAY` | Manifest (sticky) |
| Power connected / disconnected | `ACTION_POWER_CONNECTED` / `ACTION_DISCONNECTED` | Manifest |
| Screen on / off | `ACTION_SCREEN_ON` / `ACTION_SCREEN_OFF` | Dynamic (in service) |
| Boot completed | `ACTION_BOOT_COMPLETED` | Manifest (with `RECEIVE_BOOT_COMPLETED`) |
| User present (unlock) | `ACTION_USER_PRESENT` | Dynamic |

**Design rule:** Receivers must not contain logic. They convert the intent into a `DeviceEvent` and pass it to the Event Layer. A receiver that directly triggers a notification is a bug.

### 4.3 WorkManager

Used for **non-real-time, deferred periodic tasks**. These are tasks where a delay of minutes to hours is acceptable.

| Task | Period | Why WorkManager |
|------|--------|-----------------|
| Mood decay tick | Every 30 min | Does not require exact timing. WorkManager respects Doze mode. |
| Health check / self-diagnostic | Every 6 hours | Low priority, battery-friendly. |
| Event log cleanup | Daily | Deferred, can survive app restart. |

**Important:** WorkManager tasks should be set as `EXpedited` only when they genuinely need timely execution. For most personality-related tasks, regular `PeriodicWorkRequest` with a flex interval suffices.

### 4.4 Task-to-Mechanism Decision Table

| Requirement | Mechanism | Rationale |
|------------|-----------|-----------|
| Continuous sensor monitoring | Foreground Service | SensorManager requires a registered context; FG service keeps process alive |
| Instant system broadcast (battery, screen) | Broadcast Receiver | System guarantees delivery; low latency |
| Once-daily maintenance | WorkManager | Battery-friendly, survives reboots |
| Boot-time restart | Manifest Receiver + FG Service | Only way to restart after reboot on modern Android |
| High-frequency sensor (accel, gyro) | Foreground Service + callbackFlow | Sensor data is real-time; no latency-tolerant alternative |

---

## 5. Device Behavior Monitoring System

### 5.1 Monitor Abstraction

Every monitor implements a common contract:

```kotlin
interface Monitor<T : DeviceEvent> {
    /** Start listening. Called when the foreground service starts. */
    fun start()

    /** Stop listening. Called when the foreground service stops. */
    fun stop()

    /** Observable event stream. */
    val events: Flow<T>
}
```

This uniform interface allows the Event Layer to treat all monitors identically:

```kotlin
class EventDispatcher(
    private val monitors: List<Monitor<out DeviceEvent>>
) {
    fun start() {
        monitors.forEach { it.start() }
        monitors.forEach { monitor ->
            scope.launch {
                monitor.events.collect { event ->
                    dispatch(event)
                }
            }
        }
    }
}
```

### 5.2 Sensor Monitor

**What it monitors:**

- Accelerometer (TYPE_ACCELEROMETER)
- Gyroscope (TYPE_GYROSCOPE) — optional, for tilt/rotation detection

**Detected patterns:**

| Pattern | Sensor | Approach |
|---------|--------|----------|
| SHAKE | Accelerometer | Magnitude deviation count over time window |
| IMPACT | Accelerometer | Peak magnitude > threshold from still baseline |
| DROP | Accelerometer | Free-fall (mag ≈ 0) → impact sequence |
| TILT | Accelerometer / Gyroscope | Gravity axis shift; angular velocity integration |

**Implementation notes:**

- Sampling at `SENSOR_DELAY_UI` (~60ms) is sufficient. `SENSOR_DELAY_GAME` is unnecessary for non-real-time detection.
- Register only when the screen is on (screen-off is irrelevant for motion detection).
- Use `callbackFlow` to convert listener callbacks into a structured `Flow`, matching the existing `MotionSensorManager` pattern.

### 5.3 Battery Monitor

**What it monitors:**

| Event | Trigger |
|-------|---------|
| CHARGE_START | `ACTION_POWER_CONNECTED` |
| CHARGE_END | `ACTION_POWER_DISCONNECTED` |
| LOW_BATTERY | `ACTION_BATTERY_LOW` (threshold: ~15%) |
| BATTERY_OK | `ACTION_BATTERY_OKAY` (above ~15%) |
| FULL_BATTERY | `ACTION_BATTERY_CHANGED` with level=100 (polling) |

**Design note:** `ACTION_BATTERY_CHANGED` is a sticky broadcast — the last value is always available via `registerReceiver(null, intentFilter)`. This allows querying the current level without a registered receiver.

### 5.4 Screen Monitor

**What it monitors:**

| Event | Source |
|-------|--------|
| SCREEN_ON | `ACTION_SCREEN_ON` |
| SCREEN_OFF | `ACTION_SCREEN_OFF` |
| USER_PRESENT | `ACTION_USER_PRESENT` (unlock) |
| LONG_SESSION | Time accumulator: if cumulative screen-on exceeds threshold (e.g. 2h continuous) |

**Implementation:** Screen events are registered dynamically (not in manifest) because `ACTION_SCREEN_ON/OFF` are not delivered to manifest-declared receivers since Android 8 (except when registered in a FG service).

The `LONG_SESSION` event requires a timer: when `SCREEN_ON` fires, start counting; on `SCREEN_OFF`, persist elapsed; when cumulative exceeds threshold, emit `LONG_USAGE`.

### 5.5 Device State Monitor

| Event | Source | Notes |
|-------|--------|-------|
| TIME_LATE_NIGHT | System time | Emit at configurable "late" threshold (default: 23:00-05:00) |
| TIME_MORNING | System time | Emit on first SCREEN_ON after 05:00 |
| NETWORK_CONNECTED | `CONNECTIVITY_ACTION` (or `NetworkCallback`) | Requires `ACCESS_NETWORK_STATE` |
| DOZE_MODE | `ACTION_POWER_SAVE_MODE_CHANGED` | Battery saver activation/deactivation |

**Privacy:** No location, no WiFi SSID, no cellular tower ID. Device state monitoring is limited to boolean/integer system properties that carry no user-identifiable information.

---

## 6. Event System Design

### 6.1 Why an Event Layer Exists

Without an explicit event layer, monitors call response logic directly. This creates a rigid graph:

```
ScreenMonitor → if(event == SCREEN_ON) → playSound()
```

This is brittle for three reasons:

1. Adding a new monitor requires modifying the response code.
2. Rate-limiting is duplicated across monitors.
3. Personality cannot intercept or transform events.

With an event layer:

```
ScreenMonitor → DeviceEvent.SCREEN_ON → EventDispatcher → [RateLimiter] → [QuietHours] → PersonalityEngine
```

The response code never touches the monitor code. The event layer is the narrow waist of the system.

### 6.2 Event Model

```kotlin
sealed class DeviceEvent {
    data class Shake(val intensity: Float) : DeviceEvent()
    data class Impact(val intensity: Float) : DeviceEvent()
    data class Drop(val force: Float) : DeviceEvent()
    data object ChargeStart : DeviceEvent()
    data object ChargeEnd : DeviceEvent()
    data object LowBattery : DeviceEvent()
    data object BatteryOk : DeviceEvent()
    data object BatteryFull : DeviceEvent()
    data object ScreenOn : DeviceEvent()
    data object ScreenOff : DeviceEvent()
    data object UserPresent : DeviceEvent()
    data class LongUsage(val minutes: Int) : DeviceEvent()
    data object TimeLateNight : DeviceEvent()
    data object TimeMorning : DeviceEvent()
    data object NetworkConnected : DeviceEvent()
    data object DozeModeOn : DeviceEvent()
    data object DozeModeOff : DeviceEvent()
}
```

### 6.3 EventDispatcher

```kotlin
class EventDispatcher(
    private val personalityEngine: PersonalityEngine
) {
    private val events = MutableSharedFlow<DeviceEvent>(
        replay = 0,
        extraBufferCapacity = 32
    )

    fun dispatch(event: DeviceEvent) {
        events.tryEmit(event)
    }

    fun start(scope: CoroutineScope) {
        scope.launch {
            events
                .applyCooldowns()
                .applyQuietHours()
                .collect { event ->
                    personalityEngine.process(event)
                }
        }
    }

    private fun Flow<DeviceEvent>.applyCooldowns(): Flow<DeviceEvent> {
        // Debounce rapid events, deduplicate within time windows
        return this.debounce {
            when (it) {
                is DeviceEvent.Shake -> 2000L
                is DeviceEvent.Impact -> 3000L
                is DeviceEvent.Drop -> 30000L
                else -> 500L
            }
        }
    }

    private fun Flow<DeviceEvent>.applyQuietHours(): Flow<DeviceEvent> {
        // Suppress events during user-defined quiet window
        // Filter out events with priority below threshold
        return this.filter {
            !isInQuietHours() || it is DeviceEvent.LowBattery
        }
    }
}
```

### 6.4 Event Flow Summary

```
Monitor → DeviceEvent → EventDispatcher
                           │
                    (applyCooldowns)
                    (applyQuietHours)
                    (applyDeduplication)
                           │
                    PersonalityEngine.process(event)
```

---

## 7. Personality Engine (Core)

### 7.1 Purpose

The personality engine is the system's crown jewel. It converts a raw `DeviceEvent` into a `ResponseIntent` — a structured description of *what* to say and *how* to deliver it — guided by a character's personality profile and current mood.

```
DeviceEvent.LowBattery(15%)
    │
    ▼
PersonalityEngine.process(event)
    │
    ├── Character: "GrumpyBot"
    ├── Mood: TIRED (shifted -2 toward NEGATIVE because this is 3rd low-battery this week)
    ├── ResponseSelector.select("low_battery", mood)
    │   └── Returns ResponseIntent(
    │           text = "又没电了… 你一天充几次啊",
    │           channel = NOTIFICATION,
    │           priority = NORMAL,
    │           sound = GROAN,
    │           vibration = SHORT
    │       )
    │
    ▼
ResponseLayer.execute(intent)
```

### 7.2 Character Personality

```kotlin
data class CharacterProfile(
    val id: String,
    val name: String,
    /** Base personality traits, each 0.0–1.0 */
    val traits: PersonalityTraits,
    /** Response templates keyed by event type */
    val responses: Map<String, List<ResponseTemplate>>,
    /** Mood inertia: how slowly mood changes (0.0 = instant, 1.0 = never) */
    val moodInertia: Float = 0.7f
)

data class PersonalityTraits(
    val grumpy: Float = 0.0f,      // tendency to complain
    val energetic: Float = 0.5f,   // tendency to be excited
    val caring: Float = 0.5f,      // tendency to express concern
    val sarcastic: Float = 0.0f    // tendency to use irony
)
```

### 7.3 Mood System

Mood is a 2-dimensional valence-arousal model, not a simple list of states:

```kotlin
data class Mood(
    /** -1.0 (negative) to +1.0 (positive) */
    val valence: Float = 0.0f,
    /** -1.0 (lethargic) to +1.0 (alert) */
    val arousal: Float = 0.0f
)
```

**Mood transitions:** Each event type has a defined valence/arousal delta. The current mood moves toward the delta, modulated by `moodInertia`. This prevents single events from causing wild mood swings while still allowing gradual drift.

```kotlin
fun applyEvent(mood: Mood, event: DeviceEvent): Mood {
    val delta = when (event) {
        is DeviceEvent.Shake -> MoodDelta(-0.1f, +0.3f)     // negative, arousing
        is DeviceEvent.LowBattery -> MoodDelta(-0.2f, -0.1f) // negative, draining
        is DeviceEvent.ChargeStart -> MoodDelta(+0.2f, +0.1f) // positive, calming
        is DeviceEvent.ScreenOn -> MoodDelta(0.0f, +0.2f)    // neutral, alerting
        // ...
    }
    return Mood(
        valence = mood.valence + (delta.valence - mood.valence) * (1 - inertia),
        arousal = mood.arousal + (delta.arousal - mood.arousal) * (1 - inertia)
    )
}
```

### 7.4 Response Selection

Response selection is a weighted random draw:

```kotlin
data class ResponseTemplate(
    val text: String,
    val channel: ResponseChannel,       // NOTIFICATION, SOUND, VIBRATION, SILENT
    val priority: ResponsePriority,    // LOW, NORMAL, HIGH
    val moodFilter: MoodRange,         // only eligible within this mood range
    val probability: Float = 1.0f,     // relative weight in the draw
    val cooldownMinutes: Long = 30L    // cannot repeat within this window
)

fun select(event: DeviceEvent, mood: Mood, profile: CharacterProfile): ResponseIntent {
    val templates = profile.responses[event::class.simpleName].orEmpty()
        .filter { it.moodFilter.contains(mood) }
        .filterNot { it.isOnCooldown() }
        .let { weightedPick(it) }

    // Apply mood to the selected template
    return templates?.toIntent(event, mood)
        ?: fallbackResponse(event, mood)
}
```

### 7.5 Why Not a State Machine

A finite state machine requires enumerating all states and transitions. For a system where events are diverse and characters are user-customizable, an FSM becomes combinatorial.

The valence-arousal mood model + weighted response selection scales without new states. Adding a new character means adding a response table — not rewiring a graph.

### 7.6 Fallback Response

If no template matches (all on cooldown, or mood-filters exclude everything), the engine returns a `ResponseIntent` with channel=`SILENT`. The event is acknowledged internally but the user is not disturbed. This is a feature, not a gap — silence during high-frequency events is a built-in anti-spam mechanism.

---

## 8. Response System Design

### 8.1 ResponseIntent

```kotlin
data class ResponseIntent(
    val text: String?,
    val channel: ResponseChannel,
    val priority: ResponsePriority,
    val sound: SoundType? = null,
    val vibration: VibrationPattern? = null
)

enum class ResponseChannel { NOTIFICATION, SOUND, VIBRATION, SILENT }
enum class ResponsePriority { LOW, NORMAL, HIGH }
enum class SoundType { CHIME, GROAN, ALERT, JOY }
enum class VibrationPattern { SHORT, LONG, DOUBLE }
```

### 8.2 Channel Implementations

**Notification channel:**

- Always uses a single designated notification channel (`NotificationCompat`).
- Priority maps to `NotificationCompat.PRIORITY_LOW / DEFAULT / HIGH`.
- High-priority notifications can use `HeadsUpNotification` (Android 10+) or `BubbleMetadata` (Android 11+).
- Text is truncated to 2 lines maximum. Lengthy personalities must be concise.

**Sound:**

- Uses `MediaPlayer` with preloaded short audio clips (<2s each).
- Sound clips are bundled as raw resources and keyed by `SoundType`.
- Volume is set low by default (0.3–0.5 of media volume).
- Sound is skipped if `RINGER_MODE_SILENT` or `RINGER_MODE_VIBRATE`.

**Vibration:**

- Uses `Vibrator` / `VibratorManager` (API 31+).
- Pattern durations: SHORT=100ms, LONG=400ms, DOUBLE=[0,100,100,100].
- Vibration is skipped if `RINGER_MODE_SILENT`.

**Silent:**

- The event is logged to internal storage. No user-facing output.
- Used during quiet hours, high-frequency event bursts, or when all templates are on cooldown.

### 8.3 Anti-Interruption Design

| Measure | Implementation |
|---------|---------------|
| Global cooldown | At most 1 notification per 10 minutes |
| Per-template cooldown | Each template has a configurable cooldown (default 30 min) |
| Quiet hours | Configurable time window where only HIGH priority responses fire |
| Sound mute | Sound responses become vibration when ringer is silent |
| Vibration mute | Vibration responses become silent during quiet hours |
| Max daily count | HALT all output after 20 notifications per day (rolling window) |

The goal is not zero interruptions — it is *meaningful* interruptions. Every output should feel intentional, not mechanical.

---

## 9. Recommended Project Structure

```
app/
│
├── service/
│   ├── CompanionForegroundService.kt     # Foreground service lifecycle
│   └── CompanionBootReceiver.kt          # BOOT_COMPLETED receiver
│
├── monitor/
│   ├── Monitor.kt                        # Monitor<T> interface
│   ├── SensorMonitor.kt                  # Accelerometer (+ gyroscope)
│   ├── BatteryMonitor.kt                 # Power events via BroadcastReceiver
│   ├── ScreenMonitor.kt                  # Screen on/off, unlock
│   └── DeviceStateMonitor.kt             # Time, doze, network
│
├── event/
│   ├── DeviceEvent.kt                    # Sealed event class
│   └── EventDispatcher.kt                # Flow-based dispatch, cooldowns, quiet hours
│
├── personality/
│   ├── CharacterProfile.kt               # Character data model
│   ├── PersonalityTraits.kt              # Trait definitions
│   ├── Mood.kt                           # Valence-arousal mood model
│   ├── ResponseSelector.kt               # Weighted template selection
│   └── PersonalityEngine.kt              # Top-level orchestrator
│
├── response/
│   ├── ResponseIntent.kt                 # Data model
│   ├── NotificationService.kt            # Notification channel + posting
│   ├── SoundService.kt                   # SoundPool / MediaPlayer wrapper
│   └── VibrationService.kt               # Vibrator pattern player
│
├── storage/
│   ├── CharacterRepository.kt            # Load/save character profile + mood
│   └── EventLog.kt                       # Recent event ring buffer (persisted)
│
└── ui/
    ├── MainActivity.kt                   # Optional: event log viewer
    └── SettingsFragment.kt                # Character picker, quiet hours, rate limits
```

### Module Dependency

```
service → monitor → event → personality → response
                                      ↘
                                   storage
                                      ↙
                                    ui
```

`ui` has no outbound dependencies — the app runs without UI. `storage` is a leaf module accessed by `personality` and optionally by `ui`.

---

## 10. Data Model Design

### 10.1 Character

```kotlin
data class Character(
    val id: String,
    val name: String,
    val profile: CharacterProfile,
    val currentMood: Mood,
    val createdAt: Long,
    val lastActiveAt: Long
)
```

### 10.2 DeviceEvent (Event Log Entry)

```kotlin
data class EventLogEntry(
    val type: String,            // e.g. "SCREEN_ON", "LOW_BATTERY"
    val timestamp: Long,
    val metadata: Map<String, String> = emptyMap(),
    val responseGiven: Boolean
)
```

### 10.3 ResponseTemplate

```kotlin
data class ResponseTemplate(
    val eventType: String,
    val text: String,
    val channel: ResponseChannel,
    val priority: ResponsePriority,
    val moodFilter: MoodRange = MoodRange.ALL,
    val weight: Float = 1.0f,
    val cooldownMinutes: Long = 30L
)

data class MoodRange(
    val valenceMin: Float = -1.0f,
    val valenceMax: Float = 1.0f,
    val arousalMin: Float = -1.0f,
    val arousalMax: Float = 1.0f
) {
    fun contains(mood: Mood): Boolean =
        mood.valence in valenceMin..valenceMax &&
        mood.arousal in arousalMin..arousalMax

    companion object {
        val ALL = MoodRange()
    }
}
```

### 10.4 Persistent Storage

Serialization via `SharedPreferences` or `DataStore<Preferences>`:

```
prefs: {
  "character_id": "grumpy_bot",
  "mood_valence": 0.3,
  "mood_arousal": -0.1,
  "last_active": 1721433600000,
  "cooldowns": { "SCREEN_ON_0": 1721433500000, ... },
  "event_log": [ ... ring buffer of last 50 events ... ]
}
```

DataStore is preferred over SharedPreferences for coroutine-native async access. Room is not needed — there is exactly one character and one event log with a fixed-size ring buffer.

---

## 11. MVP Development Roadmap

### Phase 1 — Skeleton (Week 1)

**Goal:** A foreground service that starts and stays alive.

- `CompanionForegroundService` with persistent notification
- `CompanionBootReceiver` that restarts the service on boot
- Verify: service stays alive for 24h+ on device

**No sensors. No events. No personality.** Just proof that the process survives.

---

### Phase 2 — Monitors (Week 2)

**Goal:** Detect real device events and log them.

- `BatteryMonitor` → log `CHARGE_START`, `CHARGE_END`, `LOW_BATTERY`
- `ScreenMonitor` → log `SCREEN_ON`, `SCREEN_OFF`, `USER_PRESENT`
- `SensorMonitor` → detect `SHAKE`, `IMPACT` (reuse from existing project)
- All events written to `EventLog`

**Verify:** Run for one day, check log for 10+ events across categories.

---

### Phase 3 — Event Layer (Week 3)

**Goal:** Filtered, rate-limited event stream.

- `EventDispatcher` with cooldown and quiet-hours
- Event log persistence (ring buffer, last 50 entries)

**Verify:** Simulate rapid `SCREEN_ON` → verify dedup. Verify quiet hours suppress output.

---

### Phase 4 — Fixed Response (Week 4)

**Goal:** Events produce real feedback.

- `NotificationService` posts notifications with templated text
- `SoundService` plays short sounds
- `VibrationService` vibrates on HIGH priority
- Hardcoded response map (no personality yet)

**Verify:** Charge phone → hear a chime. Shake phone → notification appears.

---

### Phase 5 — Personality Engine (Week 5–6)

**Goal:** Characters diverge in behavior.

- `CharacterProfile` with traits and response tables
- `Mood` valence-arousal model with event-driven transitions
- `ResponseSelector` weighted random pick
- 2 built-in characters (e.g. "GrumpyBot" vs "SunnyBot")

**Verify:** Same event → different responses from different characters. Mood shifts over time.

---

### Phase 6 — AI-Generated Characters (Week 7+, Optional)

**Goal:** Users can create custom characters via LLM prompt.

- Prompt → LLM → structured `CharacterProfile` JSON → saved to storage
- User writes a one-line description; the system generates response templates
- See [Section 12](#12-ai-extension-path) for detailed design

**Verify:** "A sarcastic cat that hates mornings" → generated character feels distinct from built-in ones.

---

## 12. AI Extension Path

### 12.1 Scope Control

AI generation is reserved for **content creation** — what the character says and how it sounds — not for **event routing** or **response timing**. The core event loop remains deterministic and rule-based.

**Allowed:**

- Generating `CharacterProfile` JSON from natural language description
- Generating `ResponseTemplate` text for each event type
- Setting tone, vocabulary, and personality traits

**Forbidden:**

- AI deciding whether an event is delivered
- AI setting response frequency
- AI reading user data or conversation history

### 12.2 Generation Flow

```
User prompt:
  "A sleepy sloth that only responds to charging events"

System:
  1. Build system prompt with allowed event types and template schema
  2. Call LLM with structured output constraint
  3. Parse returned JSON into CharacterProfile
  4. Validate: every event type must have at least 2 templates
  5. Save to storage and set as active character

LLM output:
  {
    "name": "Sleepy Sloth",
    "traits": { "grumpy": 0.3, "energetic": 0.1, "caring": 0.6, "sarcastic": 0.0 },
    "responses": {
      "ChargeStart": [
        { "text": "Zzz… oh? Power. Good.", "channel": "NOTIFICATION", "priority": "LOW" },
        { "text": "...charging...", "channel": "SOUND", "sound": "CHIME", "priority": "LOW" }
      ],
      "LowBattery": [
        { "text": "...mm... energy... fading...", "channel": "NOTIFICATION", "priority": "HIGH" }
      ]
    }
  }
```

### 12.3 Safety Constraints

- Response text must be < 120 characters (truncated if longer).
- At least 1 LOW-priority template per event type (prevents all AI responses from being noisy).
- No template can request notification vib/sound on every event — cooldown is enforced by the engine.
- AI generation runs on-device (ML Kit or on-device LLM) or via an opt-in API call with clear privacy notice.

---

## 13. Performance & Privacy Design

### 13.1 Battery Optimization

| Strategy | Implementation |
|----------|---------------|
| Deferred sensor | Register at `SENSOR_DELAY_UI` (~60ms), not `GAME` or `FASTEST` |
| Screen-aware sensor | Unregister accelerometer when screen is off (motion has no meaning in pocket) |
| Event batching | Do not process events individually — flush every 500ms |
| WorkManager for non-urgent | Mood decay, log cleanup → WorkManager, not service thread |
| Quiet hours | Suppress all monitor processing (not just output) during sleep window |
| Cooldown | Short global cooldown (10 min between notifications) prevents wake-lock chains |

**Target:** < 1% additional battery drain per day on a 4000mAh device. Measured via `BatteryHistorian`.

### 13.2 Memory

- Event log is a fixed-size ring buffer (max 200 entries). No unbounded lists.
- Sound clips are loaded into `SoundPool` at service start (total < 500KB).
- No bitmap, no cache, no lazy-loaded resources.

### 13.3 Privacy

**What the app reads:**

| Data | Purpose | Retention |
|------|---------|-----------|
| Accelerometer values | Motion detection (SHAKE, IMPACT, DROP) | Not stored; processed in-memory |
| Battery level / charging status | Battery events | Kept in ring buffer (max 50 entries, never uploaded) |
| Screen on/off / unlock | Screen events | Same ring buffer |
| System time | Late-night / morning detection | Not stored |
| Network connectivity | Connectivity change event | Boolean only; no SSID, no IP |

**What the app NEVER reads:**

- Contacts, call logs, SMS
- Location (GPS or network)
- Calendar, email, messages
- Installed app list
- Browser history
- Microphone / camera
- WiFi SSID or BSSID
- Bluetooth device identifiers

**Permissions required:**

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.VIBRATE" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

No internet permission. No location permission. No storage permission.

**Data Transmission:** The app does not transmit data. There is no server. Character profiles are JSON files on local storage. If AI generation uses an API, that is the only network call and must be opt-in with a visible indicator.

---

## 14. Project Highlights

### 14.1 Technical Value

| Dimension | Contribution |
|-----------|-------------|
| **Android system capability** | Demonstrates deep integration with `ForegroundService`, `BroadcastReceiver`, `SensorManager`, `WorkManager`, `NotificationManager`, and `Vibrator` in a single coherent system |
| **Event-driven architecture** | Clean layered design with unidirectional data flow. Monitors never know about responses. Personality is a pluggable module, not a monolith. |
| **Personified interaction** | Valence-arousal mood model with weighted response selection produces emergent character behavior without a state machine explosion |
| **Background-first design** | The app functions without any UI layer. UI is an inspect-only add-on, not a requirement. |

### 14.2 Extensibility

- **New event source:** Write a `Monitor<T>` implementation. Register it in `EventDispatcher`. Add response templates. No other code changes.
- **New character:** Create a `CharacterProfile` with response templates. No engine changes.
- **New output channel:** Implement the channel interface. Add the channel to `ResponseIntent`. No event or personality changes.
- **AI generation:** Replaces content creation (response text, tone) without touching the routing or timing logic.

### 14.3 Architectural Constraints That Protect Quality

1. **Events are immune to character changes.** The `DeviceEvent` sealed class never carries personality data. This guarantees the event layer is reusable across any character.
2. **Personality cannot prevent event delivery.** The engine can choose *not to respond* (SILENT channel), but it cannot suppress the event. This enables accurate event logging and future analytics.
3. **Response channels are stateless.** `NotificationService`, `SoundService`, and `VibrationService` receive a `ResponseIntent` and execute it. They hold no reference to character or mood.
4. **Storage is write-only for monitors, read-only for personality.** Monitors append to the event log. Personality reads the log for mood calculation. Neither can corrupt the other's data.

### 14.4 Comparison Matrix

| Aspect | Standard Virtual Pet | This Project |
|--------|---------------------|--------------|
| Interaction | User-initiated (tap, swipe) | System-initiated (event → response) |
| Core loop | Feed → Play → Clean | Observe → Interpret → React |
| Character depth | Fixed animation set | Personality traits + mood dynamics |
| Background | Pauses when not focused | Always aware (FG service) |
| Extensibility | New minigame or costume | New monitor + new character profile |
| Battery impact | None (only when open) | <1% daily (optimized polling) |
| Privacy risk | Low (no system access) | Minimal (limited to device state) |

---

> **Closing note:** This project trades the complexity of a visual game for the subtlety of a persistent presence. The engineering challenge is not rendering — it is designing a system that knows when to speak, what to say, and when to stay silent. That restraint is the core product decision.
