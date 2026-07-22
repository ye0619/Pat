package com.example.pat.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pat.audio.AudioPlaybackState
import com.example.pat.engine.RuleEngineV2.RecentTrigger
import com.example.pat.ui.components.ActionButton
import com.example.pat.ui.components.StatusIndicator
import com.example.pat.ui.theme.AppleRadius
import com.example.pat.ui.theme.AppleSpacing
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
 * - 音频播放器
 */
@Composable
fun HomeScreen(
    isServiceRunning: Boolean,
    todayTriggerCount: Int,
    recentTriggers: List<RecentTrigger>,
    onNavigateToEventList: () -> Unit,
    isDarkTheme: Boolean = false,
    onToggleTheme: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // ── 反馈弹窗 ──
    var showFeedback by remember { mutableStateOf(false) }
    if (showFeedback) {
        val ctx = LocalContext.current
        val clipboard = remember { ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager }
        Dialog(onDismissRequest = { showFeedback = false }) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(AppleRadius.sm)
            ) {
                Column(modifier = Modifier.padding(AppleSpacing.lg)) {
                    Text("反馈", style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(AppleSpacing.md))
                    Text("点击可复制：", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(AppleSpacing.sm))
                    val githubUrl = "https://github.com/ye0619/Pat"
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable {
                            clipboard.setPrimaryClip(ClipData.newPlainText("GitHub", githubUrl))
                            Toast.makeText(ctx, "已复制 GitHub 地址", Toast.LENGTH_SHORT).show()
                        },
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = githubUrl,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelMedium,
                            softWrap = true
                        )
                    }
                    Spacer(Modifier.height(AppleSpacing.xs))
                    val email = "2827135233@qq.com"
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable {
                            clipboard.setPrimaryClip(ClipData.newPlainText("邮箱", email))
                            Toast.makeText(ctx, "已复制邮箱地址", Toast.LENGTH_SHORT).show()
                        },
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = email,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelMedium,
                            softWrap = true
                        )
                    }
                    Spacer(Modifier.height(AppleSpacing.md))
                    TextButton(
                        onClick = { showFeedback = false },
                        modifier = Modifier.align(Alignment.End)
                    ) { Text("关闭", color = MaterialTheme.colorScheme.primary) }
                }
            }
        }
    }

    // ── 首次用户须知 ──
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("motionpet_ui_state", android.content.Context.MODE_PRIVATE) }
    var showDisclaimer by remember {
        mutableStateOf(!prefs.getBoolean("disclaimer_accepted", false))
    }
    if (showDisclaimer) {
        Dialog(
            onDismissRequest = { }
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp)
                ) {
                    Text(
                        "用户须知",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "本项目纯属娱乐，所有反馈内容仅供消遣。\n\n请勿将本应用用于任何严肃场合。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = {
                            prefs.edit().putBoolean("disclaimer_accepted", true).apply()
                            showDisclaimer = false
                        }) { Text("我知道了") }
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── 顶栏：运行状态 + 主题切换 + 反馈按钮 ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatusIndicator(isActive = isServiceRunning)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onToggleTheme != null) {
                    IconButton(onClick = onToggleTheme) {
                        Text(if (isDarkTheme) "☀️" else "🌙", style = MaterialTheme.typography.titleMedium)
                    }
                }
                IconButton(onClick = { showFeedback = true }) {
                    Text("💬", style = MaterialTheme.typography.titleMedium)
                }
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
                    fontWeight = FontWeight.SemiBold,
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

        // ── 事件管理入口 ──
        ActionButton(
            label = "事件管理",
            onClick = onNavigateToEventList,
            modifier = Modifier.fillMaxWidth()
        )
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
                text = trigger.displayName,
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

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
