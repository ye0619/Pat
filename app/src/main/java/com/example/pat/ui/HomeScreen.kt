package com.example.pat.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.pat.audio.AudioPlaybackState
import com.example.pat.engine.EventDispatcher.RecentTrigger
import com.example.pat.event.EventType
import com.example.pat.ui.theme.PatTheme
import java.text.SimpleDateFormat
import java.util.*

/**
 * 首页 —— MotionPet 主界面。
 *
 * 显示：
 * - 应用名称 + 运行状态指示
 * - 今日触发次数
 * - 最近事件列表
 * - 进入事件管理页的入口
 */
@Composable
fun HomeScreen(
    isServiceRunning: Boolean,
    todayTriggerCount: Int,
    recentTriggers: List<RecentTrigger>,
    onNavigateToEventList: () -> Unit,
    onTestEvent: ((EventType) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── 标题 ──
        Text(
            text = "Pat",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ── 运行状态 ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (isServiceRunning)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.padding(4.dp)
            ) {
                Text(
                    text = if (isServiceRunning) "● 正在运行" else "○ 已停止",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isServiceRunning)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── 音频播放状态 ──
        val isAudioPlaying by AudioPlaybackState.isPlaying.collectAsState()
        val audioName by AudioPlaybackState.currentName.collectAsState()
        if (isAudioPlaying) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("正在播放", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(audioName, style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { AudioPlaybackState.togglePause() }) {
                        Text("⏯", style = MaterialTheme.typography.titleMedium)
                    }
                    IconButton(onClick = { AudioPlaybackState.onStop() }) {
                        Text("⏹", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // ── 通知设置引导（国内 ROM 常需手动开启悬浮通知） ──
        val context = LocalContext.current
        NotificationGuideCard(context = context)

        Spacer(modifier = Modifier.height(12.dp))

        // ── 今日触发统计 ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "今日触发",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${todayTriggerCount}次",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── 最近事件 ──
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = "最近事件",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (recentTriggers.isEmpty()) {
                    Text(
                        text = "暂无触发记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn {
                        items(recentTriggers) { trigger ->
                            RecentTriggerRow(trigger)
                            HorizontalDivider(thickness = 0.5.dp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── 测试通知 ──
        if (onTestEvent != null) {
            var testExpanded by remember { mutableStateOf(false) }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { testExpanded = !testExpanded },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "🧪 测试通知",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = if (testExpanded) "收起 ▲" else "展开 ▼",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }

                    if (testExpanded) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "点击按钮直接触发事件通知，用于验证通知横幅、声音和震动是否正常。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TestButton("长时间使用", onClick = { onTestEvent(EventType.SCREEN_LONG_USAGE) })
                            TestButton("充电", onClick = { onTestEvent(EventType.CHARGE_START) })
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TestButton("低电量", onClick = { onTestEvent(EventType.LOW_BATTERY) })
                            TestButton("摇晃", onClick = { onTestEvent(EventType.SHAKE) })
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TestButton("撞击", onClick = { onTestEvent(EventType.IMPACT) })
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        // ── 事件管理入口 ──
        Button(
            onClick = onNavigateToEventList,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = "事件管理",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun RecentTriggerRow(trigger: RecentTrigger) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = trigger.eventTypeName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = trigger.displayText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = formatTimestamp(trigger.timestamp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 通知设置引导卡片。
 * 提示用户检查是否开启了悬浮/横幅通知（国内 ROM 常默认关闭）。
 */
@Composable
private fun NotificationGuideCard(context: android.content.Context) {
    val prefs = remember { context.getSharedPreferences("motionpet_ui_state", android.content.Context.MODE_PRIVATE) }
    var visible by remember {
        mutableStateOf(!prefs.getBoolean("notification_guide_dismissed", false))
    }

    if (!visible) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "💡 通知提示",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    visible = false
                    prefs.edit().putBoolean("notification_guide_dismissed", true).apply()
                }) {
                    Text("✕", color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "如果没有弹窗横幅，请检查：\n" +
                        "1. 系统设置 → 通知 → Pat → 开启\"悬浮通知\"\n" +
                        "2. 关闭\"设为静音\"或\"不重要通知\"",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedButton(
                onClick = {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            ) {
                Text("打开系统通知设置 →", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun RowScope.TestButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.weight(1f),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        )
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    PatTheme {
        HomeScreen(
            isServiceRunning = true,
            todayTriggerCount = 3,
            recentTriggers = listOf(
                RecentTrigger("屏幕使用过久", "别看了，我想睡觉了", System.currentTimeMillis() - 300_000),
                RecentTrigger("开始充电", "谢谢给我补充能量", System.currentTimeMillis() - 1_800_000),
                RecentTrigger("摇晃手机", "别摇我", System.currentTimeMillis() - 3_600_000)
            ),
            onNavigateToEventList = {}
        )
    }
}
