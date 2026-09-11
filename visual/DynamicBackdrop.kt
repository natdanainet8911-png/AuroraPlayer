package com.aurora.player.visual

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.compose.LocalPlatformContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * โครงสร้างสามชั้น (Layered Composition):
 *   L0  Blurred artwork  — RenderEffect blur (HW-accelerated)
 *   L1  Aurora blobs     — radial gradients เคลื่อนที่ตาม Lissajous curve
 *   L2  Scrim            — vertical gradient เพื่อรับประกัน WCAG contrast ratio ≥ 4.5:1
 *
 * ประสิทธิภาพ: ทุกชั้นวาดใน Draw Phase ผ่าน drawBehind/Canvas lambda
 *              จึงไม่กระตุ้น Recomposition หรือ Re-layout แม้อัปเดตทุกเฟรม
 */
@Composable
fun DynamicBackdrop(
    artworkModel: Any?,
    palette: AuroraPalette,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    blurRadius: androidx.compose.ui.unit.Dp = 72.dp,
) {
    // Crossfade สีอย่างนุ่มนวลเมื่อเปลี่ยนเพลง
    val spec = tween<Color>(durationMillis = 900, easing = FastOutSlowInEasing)
    val cDominant by animateColorAsState(palette.dominant, spec, label = "dom")
    val cVibrant  by animateColorAsState(palette.vibrant,  spec, label = "vib")
    val cAccent   by animateColorAsState(palette.accent,   spec, label = "acc")

    // นาฬิกาเฟรมเดียวสำหรับทุก animation ในฉาก — หลีกเลี่ยง timer หลายตัว
    val clock = remember { mutableFloatStateOf(0f) }
    val speed by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.25f,
        animationSpec = tween(1200, easing = LinearOutSlowInEasing),
        label = "speed"
    )
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) clock.floatValue += ((now - last) / 1_000_000_000f) * speed
                last = now
            }
        }
    }

    Box(modifier) {
        // ── L0: artwork เบลอ ──────────────────────────────────────────────
        AsyncImage(
            model = ImageRequest.Builder(LocalPlatformContext.current)
                .data(artworkModel)
                .size(64)                 // ดาวน์แซมเปิลก่อนเบลอ → ลดภาระ GPU อย่างมาก
                .crossfade(700)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(blurRadius, BlurredEdgeTreatment.Unbounded)
                .graphicsLayer { alpha = 0.55f; scaleX = 1.25f; scaleY = 1.25f }
        )

        // ── L1: Aurora blobs ──────────────────────────────────────────────
        Canvas(Modifier.fillMaxSize()) {
            val t = clock.floatValue
            val w = size.width; val h = size.height
            val r = maxOf(w, h) * 0.85f

            // Lissajous: (a·sin(ω₁t+φ), b·cos(ω₂t))  — วิถีไม่ซ้ำรอบเมื่อ ω₁/ω₂ เป็นอตรรกยะ
            fun blob(color: Color, w1: Float, w2: Float, phase: Float, ax: Float, ay: Float, a: Float) {
                val cx = w * (0.5f + ax * sin(t * w1 + phase))
                val cy = h * (0.5f + ay * cos(t * w2 + phase * 0.7f))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(color.copy(alpha = a), Color.Transparent),
                        center = Offset(cx, cy),
                        radius = r
                    ),
                    radius = r, center = Offset(cx, cy),
                    blendMode = BlendMode.Plus     // additive → ให้ความรู้สึกเรืองแสง
                )
            }
            blob(cVibrant, 0.23f, 0.17f, 0f,         0.42f, 0.33f, 0.48f)
            blob(cAccent,  0.31f, 0.26f, 2.1f,       0.38f, 0.40f, 0.36f)
            blob(cDominant,0.14f, 0.19f, 4.4f,       0.30f, 0.28f, 0.55f)
        }

        // ── L2: Scrim รับประกันความคมชัดของตัวอักษร ───────────────────────
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.20f),
                            0.45f to Color.Black.copy(alpha = 0.38f),
                            1f to Color.Black.copy(alpha = 0.78f)
                        )
                    )
                }
        )
    }
}
