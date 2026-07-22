package com.example.pat.ui.theme

import androidx.compose.ui.graphics.Color

// ══════════════════════════════════════════════════════════════
// Apple Design Language — 颜色 Token（Light & Dark）
// 参考：DESIGN-apple.md
// ══════════════════════════════════════════════════════════════

// ── Brand & Accent ──
val ActionBlue = Color(0xFF0066CC)           // 唯一交互色
val ActionBlueFocus = Color(0xFF0071E3)       // Focus ring
val ActionBlueOnDark = Color(0xFF2997FF)      // 深色背景上的链接蓝

// ── Light Surface ──
val CanvasWhite = Color(0xFFFFFFFF)           // 主画布
val CanvasParchment = Color(0xFFF5F5F7)       // 米白背景（交替节奏）
val SurfacePearl = Color(0xFFFAFAFC)          // 次级按钮背景

// ── Dark Surface ──
val SurfaceTile1 = Color(0xFF272729)          // 深色主 tile
val SurfaceTile2 = Color(0xFF2A2A2C)          // 深色微亮 tile
val SurfaceTile3 = Color(0xFF252527)          // 深色微暗 tile
val SurfaceBlack = Color(0xFF000000)          // 纯黑（全局导航栏）

// ── Light Text ──
val InkNearBlack = Color(0xFF1D1D1F)          // 标题/正文
val InkMuted80 = Color(0xFF333333)            // 次级正文（80%）
val InkMuted48 = Color(0xFF7A7A7A)            // 禁用文字/法律声明（48%）

// ── Dark Text ──
val BodyOnDark = Color(0xFFFFFFFF)            // 深色背景白色正文
val BodyMuted = Color(0xFFCCCCCC)             // 深色背景次级文字

// ── Hairlines & Borders ──
val DividerSoft = Color(0xFFF0F0F0)           // 次级按钮"边框"
val Hairline = Color(0xFFE0E0E0)              // 卡片 1px 边框

// ── Translucent ──
val ChipTranslucent = Color(0xFFD2D2D7)       // 圆形控制按钮底色

// ── Status Colors ──
val StatusSuccess = Color(0xFF34C759)         // 成功/运行中
val StatusWarning = Color(0xFFFF9500)         // 警告
val StatusError = Color(0xFFFF3B30)           // 错误/停止

// ══════════════════════════════════════════════════════════════
// Dark Theme 专用色
// ══════════════════════════════════════════════════════════════

/** 深色模式下的"画布"——比纯黑稍亮，OLED 友好 */
val DarkCanvas = Color(0xFF1A1A1C)

/** 深色模式下的 Surface */
val DarkSurface = Color(0xFF2C2C2E)

/** 深色模式下的卡片背景 */
val DarkSurfaceVariant = Color(0xFF3A3A3C)
