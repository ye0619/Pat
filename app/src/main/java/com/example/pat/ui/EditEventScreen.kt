package com.example.pat.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.pat.config.EventConfig
import com.example.pat.event.EventType
import com.example.pat.ui.theme.PatTheme
import java.io.File
import java.io.FileOutputStream

/**
 * 事件编辑页面。
 *
 * 允许用户修改：
 * - 反馈文本（输入框）
 * - 通知开关（Switch）
 * - 阈值滑块（Slider，仅限 SCREEN_LONG_USAGE 和 LOW_BATTERY）
 * - 语音上传（文件选择器）+ 试听按钮
 */
@Composable
fun EditEventScreen(
    config: EventConfig,
    onSave: (EventConfig) -> Unit,
    onPreviewVoice: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // ── 编辑状态 ──
    var text by remember { mutableStateOf(config.text) }
    var notificationEnabled by remember { mutableStateOf(config.notificationEnabled) }
    var threshold by remember { mutableFloatStateOf(config.threshold.toFloat()) }
    var voicePath by remember { mutableStateOf(config.voicePath) }
    var voiceFileName by remember { mutableStateOf(extractFileName(config.voicePath)) }

    // ── 文件选择器 ──
    val audioFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            val copiedPath = copyAudioToInternal(context, selectedUri)
            if (copiedPath != null) {
                voicePath = copiedPath
                voiceFileName = extractFileName(copiedPath)
            }
        }
    }

    val showThreshold = config.eventType == EventType.SCREEN_LONG_USAGE
            || config.eventType == EventType.LOW_BATTERY

    val thresholdLabel = when (config.eventType) {
        EventType.SCREEN_LONG_USAGE -> "分钟"
        EventType.LOW_BATTERY -> "%"
        else -> ""
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // ── 标题栏 ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("< 返回")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "编辑 - ${EventConfig.displayName(config.eventType)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── 反馈文本 ──
        Text(
            text = "反馈文本",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("输入反馈文本...") },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ── 通知开关 ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "通知",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "事件触发时发送通知",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = notificationEnabled,
                onCheckedChange = { notificationEnabled = it }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── 阈值滑块（仅适用的事件类型） ──
        if (showThreshold) {
            Text(
                text = "阈值: ${threshold.toInt()}$thresholdLabel",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))

            val sliderRange = when (config.eventType) {
                EventType.SCREEN_LONG_USAGE -> 30f..300f   // 30分钟 - 5小时
                EventType.LOW_BATTERY -> 5f..50f            // 5% - 50%
                else -> 0f..100f
            }

            Slider(
                value = threshold,
                onValueChange = { threshold = it },
                valueRange = sliderRange,
                steps = 0,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))
        }

        // ── 语音 ──
        Text(
            text = "语音",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 上传按钮
            OutlinedButton(
                onClick = { audioFilePicker.launch("audio/*") },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (voicePath.isBlank()) "上传音频" else "更换音频")
            }

            // 试听按钮
            if (voicePath.isNotBlank()) {
                OutlinedButton(
                    onClick = { onPreviewVoice(voicePath) }
                ) {
                    Text("试听")
                }
            }
        }

        // 已上传文件名
        if (voiceFileName.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "已上传: $voiceFileName",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Text(
            text = "支持 mp3、wav 格式",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        // ── 保存按钮 ──
        Button(
            onClick = {
                onSave(
                    config.copy(
                        text = text,
                        notificationEnabled = notificationEnabled,
                        threshold = threshold.toInt(),
                        voicePath = voicePath
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = "保存",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

/**
 * 将用户选择的音频文件复制到应用内部存储。
 *
 * @return 复制后的文件绝对路径，失败返回 null
 */
private fun copyAudioToInternal(context: Context, uri: Uri): String? {
    return try {
        // 获取原始文件名
        var fileName = "audio_${System.currentTimeMillis()}"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                fileName = cursor.getString(nameIndex)
            }
        }

        val voiceDir = File(context.filesDir, "voices")
        if (!voiceDir.exists()) voiceDir.mkdirs()

        val destFile = File(voiceDir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }

        destFile.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun extractFileName(path: String): String {
    if (path.isBlank()) return ""
    return File(path).name
}

@Preview(showBackground = true)
@Composable
private fun EditEventScreenPreview() {
    PatTheme {
        EditEventScreen(
            config = EventConfig(
                id = "1",
                eventType = EventType.SCREEN_LONG_USAGE,
                enabled = true,
                threshold = 120,
                text = "别看了，我想睡觉了",
                notificationEnabled = true
            ),
            onSave = {},
            onPreviewVoice = {},
            onBack = {}
        )
    }
}
