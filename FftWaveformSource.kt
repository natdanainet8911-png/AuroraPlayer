package com.aurora.player

import android.media.audiofx.Visualizer
import androidx.media3.session.MediaController
import com.aurora.player.core.BAR_COUNT
import com.aurora.player.core.ProceduralWaveformSource
import com.aurora.player.core.WaveformSource
import kotlin.math.*

/**
 * ดึง FFT จาก audio output mix
 * ทฤษฎี: Visualizer คืนค่า interleaved real/imaginary; magnitude m_k = sqrt(Re²+Im²)
 *        แล้วแปลงเป็นสเกลเดซิเบล L = 20·log10(m) ก่อน normalize
 *        การจัดกลุ่มแถบใช้สเกลลอการิทึม (สอดคล้องกับการรับรู้ความถี่ของมนุษย์)
 */
class FftWaveformSource : WaveformSource {

    private var visualizer: Visualizer? = null
    private val fallback = ProceduralWaveformSource()
    private val fft = ByteArray(1024)
    private val bins = FloatArray(BAR_COUNT)
    @Volatile private var active = false

    fun attach(controller: MediaController) {
        // ต้องมี RECORD_AUDIO permission; หากไม่ได้รับ → ใช้ fallback โดยอัตโนมัติ
        runCatching {
            val sessionId = 0   // 0 = output mix (ต้องมี MODIFY_AUDIO_SETTINGS)
            Visualizer(sessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, w: ByteArray?, r: Int) = Unit
                    override fun onFftDataCapture(v: Visualizer?, data: ByteArray?, rate: Int) {
                        data?.copyInto(fft, endIndex = min(data.size, fft.size))
                        computeBins(min(data?.size ?: 0, fft.size))
                    }
                }, Visualizer.getMaxCaptureRate() / 2, false, true)
                enabled = true
            }.also { visualizer = it; active = true }
        }.onFailure { active = false }
    }

    fun detach() {
        active = false
        runCatching { visualizer?.enabled = false; visualizer?.release() }
        visualizer = null
    }

    private fun computeBins(size: Int) {
        val half = size / 2
        if (half <= 2) return
        for (b in 0 until BAR_COUNT) {
            // ขอบเขตแบบลอการิทึม: f(b) = half^(b/BAR_COUNT)
            val lo = (half.toDouble().pow(b.toDouble() / BAR_COUNT)).toInt().coerceIn(1, half - 1)
            val hi = (half.toDouble().pow((b + 1.0) / BAR_COUNT)).toInt().coerceIn(lo + 1, half)
            var acc = 0f
            for (k in lo until hi) {
                val re = fft[2 * k].toFloat()
                val im = fft[2 * k + 1].toFloat()
                acc += hypot(re, im)
            }
            val mean = acc / (hi - lo)
            val db = 20f * log10(mean.coerceAtLeast(1e-3f))     // → dBFS
            bins[b] = ((db + 12f) / 46f).coerceIn(0f, 1f)        // normalize เชิงประจักษ์
        }
    }

    override fun magnitudes(out: FloatArray, timeSeconds: Float, isPlaying: Boolean) {
        if (active && isPlaying) bins.copyInto(out)
        else fallback.magnitudes(out, timeSeconds, isPlaying)
    }
}
