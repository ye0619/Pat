package com.example.pat.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// ══════════════════════════════════════════════════════════════
// Apple Design Language — Light & Dark Theme
// 参考：DESIGN-apple.md
//
// 设计原则：
// - Action Blue (#0066CC) 是唯一的交互色
// - 无渐变、无装饰性阴影
// - Light: 白色画布 + 米白 parchment 交替
// - Dark: 近黑 tile 层次 + OLED 友好背景
// ══════════════════════════════════════════════════════════════

private val AppleLightColorScheme = lightColorScheme(
    primary = ActionBlue,
    onPrimary = CanvasWhite,
    primaryContainer = ActionBlue.copy(alpha = 0.12f),
    onPrimaryContainer = ActionBlue,

    secondary = InkMuted80,
    onSecondary = CanvasWhite,
    secondaryContainer = CanvasParchment,
    onSecondaryContainer = InkNearBlack,

    tertiary = ActionBlueOnDark,
    onTertiary = CanvasWhite,
    tertiaryContainer = CanvasParchment,
    onTertiaryContainer = InkMuted80,

    background = CanvasWhite,
    onBackground = InkNearBlack,
    surface = CanvasWhite,
    onSurface = InkNearBlack,
    surfaceVariant = CanvasParchment,
    onSurfaceVariant = InkMuted48,

    error = StatusError,
    onError = CanvasWhite,
    errorContainer = StatusError.copy(alpha = 0.10f),
    onErrorContainer = StatusError,

    outline = Hairline,
    outlineVariant = DividerSoft
)

private val AppleDarkColorScheme = darkColorScheme(
    primary = ActionBlueOnDark,
    onPrimary = CanvasWhite,
    primaryContainer = ActionBlueOnDark.copy(alpha = 0.18f),
    onPrimaryContainer = ActionBlueOnDark,

    secondary = BodyMuted,
    onSecondary = DarkCanvas,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = BodyOnDark,

    tertiary = ActionBlueOnDark,
    onTertiary = CanvasWhite,
    tertiaryContainer = DarkSurfaceVariant,
    onTertiaryContainer = BodyMuted,

    background = DarkCanvas,
    onBackground = BodyOnDark,
    surface = DarkSurface,
    onSurface = BodyOnDark,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = BodyMuted,

    error = StatusError,
    onError = CanvasWhite,
    errorContainer = StatusError.copy(alpha = 0.15f),
    onErrorContainer = StatusError,

    outline = DarkSurfaceVariant,
    outlineVariant = DarkSurface
)

/**
 * Pat 主题 —— Apple Design Language。
 *
 * 自动跟随系统 Dark Mode。
 * 不使用 Material You dynamic color（会覆盖 Apple 调色板）。
 */
@Composable
fun PatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) AppleDarkColorScheme else AppleLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppleTypography,
        shapes = AppleShapes,
        content = content
    )
}
