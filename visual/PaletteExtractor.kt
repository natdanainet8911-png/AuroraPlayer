package com.aurora.player.visual

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

@Immutable
data class AuroraPalette(
    val dominant: Color = Color(0xFF1B1B2F),
    val vibrant: Color = Color(0xFF7C4DFF),
    val accent: Color = Color(0xFF00E5FF),
    val onSurface: Color = Color.White,
    val isDark: Boolean = true,
) {
    companion object { val Default = AuroraPalette() }
}

/**
 * อัลกอริทึม: Uniform Colour Quantisation (5-bit/channel → 32³ buckets)
 * ถ่วงน้ำหนักด้วยฟังก์ชันคะแนน  score = population^0.5 · S^1.4 · g(V)
 * โดย g(V) ลงโทษพิกเซลที่สว่างหรือมืดจนเกินไป (ลด bias จากพื้นหลังขาว/ดำ)
 * ความซับซ้อน O(N/stride²) — รันบน Dispatchers.Default เท่านั้น
 */
object PaletteExtractor {

    private const val SAMPLE_TARGET = 96   // ขนาดสุ่มตัวอย่างสูงสุดต่อด้าน

    suspend fun extract(image: ImageBitmap): AuroraPalette = withContext(Dispatchers.Default) {
        val pm = image.toPixelMap()
        val stride = max(1, max(pm.width, pm.height) / SAMPLE_TARGET)

        val pop = HashMap<Int, Int>(1024)
        val sumR = HashMap<Int, Float>(1024)
        val sumG = HashMap<Int, Float>(1024)
        val sumB = HashMap<Int, Float>(1024)

        var y = 0
        while (y < pm.height) {
            var x = 0
            while (x < pm.width) {
                val c = pm[x, y]
                if (c.alpha > 0.5f) {
                    val key = (((c.red * 31).toInt() and 31) shl 10) or
                              (((c.green * 31).toInt() and 31) shl 5) or
                               ((c.blue * 31).toInt() and 31)
                    pop[key] = (pop[key] ?: 0) + 1
                    sumR[key] = (sumR[key] ?: 0f) + c.red
                    sumG[key] = (sumG[key] ?: 0f) + c.green
                    sumB[key] = (sumB[key] ?: 0f) + c.blue
                }
                x += stride
            }
            y += stride
        }
        if (pop.isEmpty()) return@withContext AuroraPalette.Default

        data class Sw(val color: Color, val n: Int, val s: Float, val v: Float, val score: Float)

        val swatches = pop.entries.map { (k, n) ->
            val r = sumR[k]!! / n; val g = sumG[k]!! / n; val b = sumB[k]!! / n
            val mx = maxOf(r, g, b); val mn = minOf(r, g, b)
            val v = mx
            val s = if (mx <= 0f) 0f else (mx - mn) / mx
            // ลงโทษค่าความสว่างสุดขั้ว: g(V) = 1 - |2V - 1|^3
            val lumaWeight = 1f - abs(2f * v - 1f).pow(3f)
            val score = sqrt(n.toFloat()) * s.pow(1.4f) * lumaWeight
            Sw(Color(r, g, b), n, s, v, score)
        }

        val dominant = swatches.maxByOrNull { it.n }!!.color
        val vibrant  = swatches.maxByOrNull { it.score }!!.color
        val accent   = swatches
            .filter { it.s > 0.25f }
            .minByOrNull { hueDistance(it.color, vibrant).let { d -> abs(d - 150f) } }
            ?.color ?: vibrant

        val bgLuma = relativeLuminance(dominant)
        AuroraPalette(
            dominant = dominant.darken(0.55f),
            vibrant = vibrant.saturate(1.15f),
            accent = accent,
            onSurface = if (bgLuma < 0.45f) Color.White else Color(0xFF0E0E12),
            isDark = bgLuma < 0.45f,
        )
    }

    /** WCAG 2.1 relative luminance: L = 0.2126R + 0.7152G + 0.0722B (linearised) */
    fun relativeLuminance(c: Color): Float {
        fun lin(v: Float) = if (v <= 0.03928f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
        return 0.2126f * lin(c.red) + 0.7152f * lin(c.green) + 0.0722f * lin(c.blue)
    }

    private fun hueDistance(a: Color, b: Color): Float = abs(hue(a) - hue(b))

    private fun hue(c: Color): Float {
        val mx = maxOf(c.red, c.green, c.blue); val mn = minOf(c.red, c.green, c.blue)
        val d = mx - mn
        if (d == 0f) return 0f
        val h = when (mx) {
            c.red   -> 60f * (((c.green - c.blue) / d) % 6f)
            c.green -> 60f * (((c.blue - c.red) / d) + 2f)
            else    -> 60f * (((c.red - c.green) / d) + 4f)
        }
        return (h + 360f) % 360f
    }

    private fun Color.darken(f: Float) = Color(red * f, green * f, blue * f, alpha)
    private fun Color.saturate(f: Float): Color {
        val l = 0.2126f * red + 0.7152f * green + 0.0722f * blue
        return Color(
            (l + (red - l) * f).coerceIn(0f, 1f),
            (l + (green - l) * f).coerceIn(0f, 1f),
            (l + (blue - l) * f).coerceIn(0f, 1f),
            alpha
        )
    }
}
