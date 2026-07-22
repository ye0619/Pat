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
import com.example.pat.model.AudioType
import com.example.pat.model.ReactionPreset
import com.example.pat.ui.theme.PatTheme
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * 预设编辑页面 —— 创建或编辑自定义 ReactionPreset。
 *
 * 功能：
 * - 预设名称输入
 * - 反馈文本输入
 * - 音频上传（文件选择器）+ 试听
 * - 保存为自定义预设
 *
 * 用于：从 EditEventScreen 的"创建自定义预设"入口进入。
 */
@Composable
fun PresetEditScreen(
    eventTypeName: String,
    existingPreset: ReactionPreset? = null,
    onSave: (ReactionPreset) -> Unit,
    onPreviewAsset: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // ── 编辑状态 ──
    var text by remember { mutableStateOf(existingPreset?.text ?: "") }
    var audioPath by remember { mutableStateOf(existingPreset?.audioAssetPath ?: "") }
    var audioFileName by remember { mutableStateOf(extractFileName(existingPreset?.audioAssetPath ?: "")) }

    // ── 文件选择器 ──
    val audioFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            val copiedPath = copyAudioToInternal(context, selectedUri)
            if (copiedPath != null) {
                audioPath = copiedPath
                audioFileName = extractFileName(copiedPath)
            }
        }
    }

    val isNew = existingPreset == null

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
            TextButton(onClick = onBack) { Text("< 返回") }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isNew) "创建自定义预设" else "编辑预设",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "事件类型: $eventTypeName",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ── 反馈文本 ──
        Text("反馈文本", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("输入反馈文本...") },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ── 音频上传 ──
        Text("语音", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { audioFilePicker.launch("audio/*") },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (audioPath.isBlank()) "上传音频" else "更换音频")
            }
            if (audioPath.isNotBlank()) {
                OutlinedButton(onClick = { onPreviewAsset(audioPath) }) {
                    Text("试听")
                }
            }
        }

        if (audioFileName.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "已上传: $audioFileName",
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
                    ReactionPreset(
                        id = existingPreset?.id ?: UUID.randomUUID().toString(),
                        name = ReactionPreset.nameFromText(text.ifBlank { "自定义反馈" }),
                        text = text.ifBlank { "自定义反馈" },
                        audioAssetPath = audioPath,
                        audioType = AudioType.CUSTOM,
                        eventType = existingPreset?.eventType
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("保存预设", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 4.dp))
        }
    }
}

private fun copyAudioToInternal(context: Context, uri: Uri): String? {
    return try {
        var fileName = "audio_${System.currentTimeMillis()}"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) fileName = cursor.getString(nameIndex)
        }
        val voiceDir = File(context.filesDir, "voices")
        if (!voiceDir.exists()) voiceDir.mkdirs()
        val destFile = File(voiceDir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output -> input.copyTo(output) }
        }
        destFile.absolutePath
    } catch (e: Exception) { null }
}

private fun extractFileName(path: String): String {
    if (path.isBlank()) return ""
    return File(path).name
}

@Preview(showBackground = true)
@Composable
private fun PresetEditScreenPreview() {
    PatTheme {
        PresetEditScreen(
            eventTypeName = "长时间使用",
            onSave = {}, onPreviewAsset = {}, onBack = {}
        )
    }
}
