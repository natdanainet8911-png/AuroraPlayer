package com.aurora.player.visual

import androidx.compose.animation.core.withFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.aurora.player.core.BAR_COUNT
import com.aurora.player.core.WaveformSource
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow

/**
 * Waveform Visualizer แบบสมมาตร (mirrored bars)
 *
 * เทคนิคเชิงประสิทธิภาพ:
 *  1. Frame-driven ผ่าน withFrameNanos → sync กับ VSYNC ของ Choreographer
 *  2. อัปเดต state แบบ mutable array + tick counter → invalidate เฉพาะ Draw Phase
 *     (ไม่เกิด Recomposition หรือ Measure/Layout)
 *  3. Zero-allocation ใน hot loop — บัฟเฟอร์ทั้งหมดถูก remember ไว้ล่วงหน้า
 *
 * เทคนิคเชิงการรับรู้:
 *  - Exponential smoothing แบบอิสระจากอัตราเฟรม:  α = 1 − e^(−Δt/τ)
 *    ใช้ τ_attack สั้น (ตอบสนองไว) และ τ_release ยาว (ตกช้า) เลียนแบบ VU meter
 *  - Attack/Release asymmetry ทำให้ตามองเห็นจังหวะได้ชัดโดยไม่กระพริบ
 */
@Composable
fun WaveformVisualizer(
    source: WaveformSource,
    isPlaying: Boolean,
    color: Color,
    accent: Color,
    modifier: Modifier = Modifier,
    barCount: Int = BAR_COUNT,
    tauAttackMs: Float = 45f,
    tauReleaseMs: Float = 240f,
) {
    val raw = remember(barCount) { FloatArray(barCount) }
    val smooth = remember(barCount) { FloatArray(barCount) }
    val peaks = remember(barCount) { FloatArray(barCount) }
    val tick = remember { mutableIntStateOf(0) }

    LaunchedEffect(source, isPlaying) {
        var last = 0L
        var elapsed = 0f
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceIn(0.001f, 0.05f)
                last = now
                elapsed += dt

                source.magnitudes(raw, elapsed, isPlaying)

                val aA = 1f - exp(-dt * 1000f / tauAttackMs)
                val aR = 1f - exp(-dt * 1000f / tauReleaseMs)
                for (i in smooth.indices) {
                    val target = raw[i]
                    val a = if (target > smooth[i]) aA else aR
                    smooth[i] += (target - smooth[i]) * a
                    // Peak-hold พร้อม decay เชิงเส้น
                    peaks[i] = if (smooth[i] > peaks[i]) smooth[i]
                               else (peaks[i] - dt * 0.45f).coerceAtLeast(smooth[i])
                }
                tick.intValue++          // trigger draw-phase invalidation เท่านั้น
            }
        }
    }

    Canvas(modifier) {
        tick.intValue                    // อ่านค่าเพื่อผูก invalidation
        drawMirroredBars(smooth, peaks, color, accent)
    }
}

private fun DrawScope.drawMirroredBars(
    values: FloatArray, peaks: FloatArray, color: Color, accent: Color,
) {
    val n = values.size
    if (n == 0) return
    val slot = size.width / n
    val barW = slot * 0.56f
    val gap = (slot - barW) / 2f
    val half = size.height / 2f
    val radius = CornerRadius(barW / 2f, barW / 2f)

    val brush = Brush.verticalGradient(
        0f to accent, 0.5f to color, 1f to accent
    )

    for (i in 0 until n) {
        // Spatial envelope: ลดทอนที่ขอบซ้าย/ขวาเพื่อความกลมกลืนเชิงองค์ประกอบ
        val x = i.toFloat() / (n - 1).coerceAtLeast(1)
        val envelope = (1f - (2f * x - 1f).pow(4f)).coerceIn(0.15f, 1f)
        val h = (values[i] * envelope * half * 0.92f).coerceAtLeast(barW * 0.5f)

        drawRoundRect(
            brush = brush,
            topLeft = Offset(i * slot + gap, half - h),
            size = Size(barW, h * 2f),
            cornerRadius = radius,
        )
        // Peak marker
        val ph = (peaks[i] * envelope * half * 0.92f)
        if (ph > h + barW * 0.6f) {
            drawRoundRect(
                color = accent.copy(alpha = 0.75f),
                topLeft = Offset(i * slot + gap, half - ph - barW * 0.22f),
                size = Size(barW, barW * 0.22f),
                cornerRadius = CornerRadius(barW * 0.11f, barW * 0.11f),
            )
        }
    }
}
