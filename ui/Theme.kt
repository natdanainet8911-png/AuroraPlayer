package com.aurora.player.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.aurora.player.visual.AuroraPalette

/**
 * Theme ที่ปรับตัวตามปกอัลบั้ม (Content-Adaptive Theming)
 *
 * แนวคิดอ้างอิง: Material You (Material 3) — การสกัด seed colour จากเนื้อหา
 * แล้วสังเคราะห์ tonal palette เพื่อสร้างความสอดคล้องเชิงทัศนะ
 * ในที่นี้ใช้ palette ที่สกัดเองแทน HCT algorithm เพื่อให้ทำงานข้ามแพลตฟอร์มได้
 *
 * สีทุกค่าถูกห่อด้วย animateColorAsState เพื่อให้การเปลี่ยนธีมระหว่างเพลง
 * เป็น continuous transition มิใช่ discrete jump ซึ่งลด visual disruption
 */
@Composable
fun AuroraTheme(
    palette: AuroraPalette,
    content: @Composable () -> Unit,
) {
    val spec = tween<Color>(durationMillis = 800)

    val primary   by animateColorAsState(palette.vibrant, spec, label = "primary")
    val secondary by animateColorAsState(palette.accent, spec, label = "secondary")
    val surface   by animateColorAsState(palette.dominant, spec, label = "surface")
    val onSurface by animateColorAsState(palette.onSurface, spec, label = "onSurface")

    val scheme = if (palette.isDark) {
        darkColorScheme(
            primary = primary,
            onPrimary = contrastOn(primary),
            secondary = secondary,
            onSecondary = contrastOn(secondary),
            tertiary = secondary,
            background = surface,
            onBackground = onSurface,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surface.lighten(0.12f),
            onSurfaceVariant = onSurface.copy(alpha = 0.72f),
            outline = onSurface.copy(alpha = 0.28f),
            error = Color(0xFFFF6B6B),
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = contrastOn(primary),
            secondary = secondary,
            onSecondary = contrastOn(secondary),
            background = surface,
            onBackground = onSurface,
            surface = surface,
            onSurface = onSurface,
            outline = onSurface.copy(alpha = 0.28f),
        )
    }

    MaterialTheme(
        colorScheme = scheme,
        shapes = Shapes(
            extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp()),
            small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp()),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp()),
            large = androidx.compose.foundation.shape.RoundedCornerShape(26.dp()),
            extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(34.dp()),
        ),
        typography = auroraTypography(),
        content = content
    )
}

private fun Int.dp() = androidx.compose.ui.unit.Dp(this.toFloat())

/** เลือกสีข้อความที่ให้ contrast ratio สูงสุดตามมาตรฐาน WCAG 2.1 */
private fun contrastOn(background: Color): Color {
    val l = 0.2126f * background.red + 0.7152f * background.green + 0.0722f * background.blue
    return if (l > 0.5f) Color(0xFF0B0B14) else Color.White
}

private fun Color.lighten(f: Float) = Color(
    (red + (1f - red) * f).coerceIn(0f, 1f),
    (green + (1f - green) * f).coerceIn(0f, 1f),
    (blue + (1f - blue) * f).coerceIn(0f, 1f),
    alpha
)

@Composable
private fun auroraTypography(): Typography {
    val base = Typography()
    return base.copy(
        headlineSmall = base.headlineSmall.copy(
            fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp
        ),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Medium),
        bodyMedium = base.bodyMedium.copy(lineHeight = 20.sp),
        labelSmall = base.labelSmall.copy(letterSpacing = 0.6.sp),
    )
}
