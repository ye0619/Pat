package com.example.pat.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// ══════════════════════════════════════════════════════════════
// Apple Design Language — 圆角 & 间距 Token
// 参考：DESIGN-apple.md
// ══════════════════════════════════════════════════════════════

// ── Border Radius ──
object AppleRadius {
    val none = 0.dp
    val xs = 5.dp
    val sm = 8.dp
    val md = 11.dp
    val lg = 18.dp
    val pill = 9999.dp
}

// ── Material3 Shapes 映射 ──
val AppleShapes = Shapes(
    extraSmall = RoundedCornerShape(AppleRadius.xs),
    small = RoundedCornerShape(AppleRadius.sm),
    medium = RoundedCornerShape(AppleRadius.md),
    large = RoundedCornerShape(AppleRadius.lg),
    extraLarge = RoundedCornerShape(AppleRadius.pill)
)

// ── Spacing System ──
object AppleSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 17.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
    val section = 80.dp
}
