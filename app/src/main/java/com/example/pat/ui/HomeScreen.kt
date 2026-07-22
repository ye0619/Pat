package com.example.pat.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import com.example.pat.audio.AudioPlaybackState
import com.example.pat.engine.RuleEngineV2.RecentTrigger
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
    modifier: Modifier = Modifier
) {
    // ── 反馈弹窗 ──
    var showFeedback by remember { mutableStateOf(false) }
    if (showFeedback) {
        val ctx = LocalContext.current
        val clipboard = remember { ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager }
        AlertDialog(
            onDismissRequest = { showFeedback = false },
            title = { Text("反馈") },
            text = {
                Column {
                    Text("点击可复制：", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    val githubUrl = "https://github.com/ye0619/Pat"
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable {
                            clipboard.setPrimaryClip(ClipData.newPlainText("GitHub", githubUrl))
                            Toast.makeText(ctx, "已复制 GitHub 地址", Toast.LENGTH_SHORT).show()
                        },
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text("GitHub: $githubUrl", modifier = Modifier.padding(10.dp),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    val email = "2827135233@qq.com"
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable {
                            clipboard.setPrimaryClip(ClipData.newPlainText("邮箱", email))
                            Toast.makeText(ctx, "已复制邮箱地址", Toast.LENGTH_SHORT).show()
                        },
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text("邮箱: $email", modifier = Modifier.padding(10.dp),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showFeedback = false }) { Text("关闭") } }
        )
    }

    // ── 首次用户须知 ──
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("motionpet_ui_state", android.content.Context.MODE_PRIVATE) }
    var showDisclaimer by remember {
        mutableStateOf(!prefs.getBoolean("disclaimer_accepted", false))
    }
    if (showDisclaimer) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("用户须知") },
            text = {
                Text("本项目纯属娱乐，所有反馈内容仅供消遣。\n\n请勿将本应用用于任何严肃场合。")
            },
            confirmButton = {
                TextButton(onClick = {
                    prefs.edit().putBoolean("disclaimer_accepted", true).apply()
                    showDisclaimer = false
                }) { Text("我知道了") }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── 顶栏：运行状态 + 反馈按钮 ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (isServiceRunning)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.errorContainer
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
            IconButton(onClick = { showFeedback = true }) {
                Text("💬", style = MaterialTheme.typography.titleMedium)
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
