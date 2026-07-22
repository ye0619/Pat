package com.example.pat.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pat.ui.theme.AppleSpacing
import com.example.pat.ui.theme.StatusError
import com.example.pat.ui.theme.StatusSuccess

// ══════════════════════════════════════════════════════════════
// Apple 风格可复用组件 — 纯 UI，无业务逻辑
// ══════════════════════════════════════════════════════════════

/**
 * 状态指示器 — 显示服务运行/停止状态。
 */
@Composable
fun StatusIndicator(
    isActive: Boolean,
    activeLabel: String = "正在运行",
    inactiveLabel: String = "已停止",
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = if (isActive)
            StatusSuccess.copy(alpha = 0.12f)
        else
            StatusError.copy(alpha = 0.10f),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = if (isActive) StatusSuccess else StatusError,
                modifier = Modifier.size(6.dp)
            ) {}
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (isActive) activeLabel else inactiveLabel,
                style = MaterialTheme.typography.labelMedium,
                color = if (isActive) StatusSuccess else StatusError
            )
        }
    }
}

/**
 * 事件卡片 — 统一的事件展示。
 *
 * 用于 EventListScreen 展示预设事件和自定义事件。
 */
@Composable
fun EventCard(
    name: String,
    subtitle: String,
    feedback: String,
    enabled: Boolean,
    showConflict: Boolean = false,
    showDelete: Boolean = false,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (enabled)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(AppleSpacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (showConflict) {
                            Spacer(Modifier.width(AppleSpacing.xxs))
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    text = "冲突",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
            if (feedback.isNotBlank()) {
                Spacer(Modifier.height(AppleSpacing.xs))
                Text(
                    text = feedback,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(AppleSpacing.xs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (showDelete && onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text(
                            text = "删除",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
                TextButton(onClick = onEdit) {
                    Text(
                        text = "编辑",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

/**
 * Apple 风格按钮 — 胶囊形蓝色主 CTA。
 */
@Composable
fun ActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: ActionButtonVariant = ActionButtonVariant.Primary
) {
    when (variant) {
        ActionButtonVariant.Primary -> Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.height(44.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
        }
        ActionButtonVariant.Secondary -> TextButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

enum class ActionButtonVariant { Primary, Secondary }
