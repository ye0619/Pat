package com.example.pat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.pat.event.DeviceEvent
import com.example.pat.event.DeviceEventLogEntry
import com.example.pat.event.toDisplayLabel
import com.example.pat.sensor.AccelData
import com.example.pat.ui.theme.PatTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * 传感器调试界面。
 *
 * 实时显示：
 * - 传感器运行状态（Running / Stopped）
 * - 三轴实时数据（X, Y, Z）
 * - 最近检测到的事件
 * - 事件历史日志（滚动列表）
 *
 * 提供手动启动/停止控制，方便验证生命周期管理。
 */
@Composable
fun SensorDebugScreen(
    isSensorRunning: Boolean,
    sensorDataFlow: Flow<AccelData>,
    latestData: AccelData,
    lastEvent: DeviceEvent?,
    sensorFrameCount: Int = 0,
    eventLog: List<DeviceEventLogEntry>,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    onTestEventClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val sensorData by sensorDataFlow.collectAsState(initial = latestData)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── 标题 ──
        Text(
            text = "MotionPet",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ── 传感器状态 ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isSensorRunning)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sensor:",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isSensorRunning) "Running" else "Stopped",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isSensorRunning)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isSensorRunning) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "#$sensorFrameCount",
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── XYZ 实时数据 ──
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    text = "实时传感器数据",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                SensorValueRow("X", sensorData.x, "m/s²")
                SensorValueRow("Y", sensorData.y, "m/s²")
                SensorValueRow("Z", sensorData.z, "m/s²")
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Magnitude: ${"%.2f".format(sensorData.magnitude)} m/s²",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── 最近事件 ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (lastEvent != null)
                    MaterialTheme.colorScheme.tertiaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    text = "Last Event",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = lastEvent?.toDisplayLabel() ?: "NONE",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── 事件日志 ──
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                Text(
                    text = "Event Log (${eventLog.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))

                if (eventLog.isEmpty()) {
                    Text(
                        text = "No events yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn {
                        items(eventLog) { entry ->
                            EventLogRow(entry)
                            HorizontalDivider(thickness = 0.5.dp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── 手动控制按钮 ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onStartClick,
                enabled = !isSensorRunning,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Start")
            }
            Button(
                onClick = onStopClick,
                enabled = isSensorRunning,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Stop")
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Button(
            onClick = onTestEventClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary
            )
        ) {
            Text("Test Event (Shake)")
        }
    }
}

@Composable
private fun SensorValueRow(label: String, value: Float, unit: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(28.dp)
        )
        Text(
            text = "${"%10.3f".format(value)}  $unit",
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun EventLogRow(entry: DeviceEventLogEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = entry.formattedTime,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp)
        )
        Text(
            text = entry.displayLabel,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SensorDebugScreenPreview() {
    PatTheme {
        SensorDebugScreen(
            isSensorRunning = true,
            sensorDataFlow = MutableSharedFlow(),
            latestData = AccelData(9.81f, 0.12f, -0.45f, 0L),
            lastEvent = DeviceEvent.Shake,
            eventLog = listOf(
                DeviceEventLogEntry(System.currentTimeMillis() - 3000, DeviceEvent.Shake),
                DeviceEventLogEntry(System.currentTimeMillis() - 8000, DeviceEvent.ScreenWake),
                DeviceEventLogEntry(System.currentTimeMillis() - 15000, DeviceEvent.ChargeStart)
            ),
            onStartClick = {},
            onStopClick = {},
            modifier = Modifier.fillMaxSize()
        )
    }
}
