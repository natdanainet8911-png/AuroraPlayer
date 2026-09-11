package com.aurora.player

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import androidx.core.content.ContextCompat
import com.aurora.player.core.BAR_COUNT
import com.aurora.player.core.ProceduralWaveformSource
import com.aurora.player.core.WaveformSource
import kotlin.math.*

/**
 * แหล่งสเปกตรัมจริงจาก AudioEffect framework
 *
 * ห่วงโซ่การประมวลผล:
 *   PCM → Visualizer FFT → magnitude m_k = √(Re²+Im²)
 *       → dB: L_k = 20·log₁₀(m_k)
 *       → log-spaced binning (สอดคล้องกับ Bark/Mel scale โดยประมาณ)
 *       → normalisation → [0,1]
 *
 * เงื่อนไขความพร้อมใช้งาน (ทั้งสามข้อต้องเป็นจริง):
 *   1. ได้รับ RECORD_AUDIO permission
 *   2. sessionId ≠ AUDIO_SESSION_ID_UNSET
 *   3. อุปกรณ์รองรับ AudioEffect (emulator บางรุ่นไม่รองรับ)
 * มิฉะนั้นระบบจะ degrade อย่างนุ่มนวลไปใช้ ProceduralWaveformSource
 */
class FftWaveformSource(private val context: Context) : WaveformSource {

    private var visualizer: Visualizer? = null
    private val fallback = ProceduralWaveformSource()
    private val bins = FloatArray(BAR_COUNT)
    @Volatile private var active = false
    private var attachedSessionId = -1

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun attach(sessionId: Int) {
        if (sessionId <= 0 || sessionId == attachedSessionId) return
        if (!hasPermission()) { active = false; return }
        detach()

        runCatching {
            Visualizer(sessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]   // ปกติ 1024
                scalingMode = Visualizer.SCALING_MODE_NORMALIZED
                measurementMode = Visualizer.MEASUREMENT_MODE_NONE
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(v: Visualizer?, d: ByteArray?, r: Int) = Unit
                        override fun onFftDataCapture(v: Visualizer?, data: ByteArray?, rate: Int) {
                            data?.let { computeBins(it) }
                        }
                    },
                    Visualizer.getMaxCaptureRate() / 2,   // ~10–20 Hz เพียงพอ ลดภาระ CPU
                    /* waveform = */ false,
                    /* fft = */ true
                )
                enabled = true
            }.also {
                visualizer = it
                attachedSessionId = sessionId
                active = true
            }
        }.onFailure {
            active = false
            attachedSessionId = -1
        }
    }

    fun detach() {
        active = false
        attachedSessionId = -1
        runCatching {
            visualizer?.enabled = false
            visualizer?.release()
        }
        visualizer = null
    }

    /** แปลง FFT เป็นแถบความถี่แบบลอการิทึม — ไม่มีการ allocate ใน hot path */
    private fun computeBins(fft: ByteArray) {
        val half = fft.size / 2
        if (half <= 4) return
        val logBase = half.toDouble()
        for (b in 0 until BAR_COUNT) {
            val lo = logBase.pow(b.toDouble() / BAR_COUNT).toInt().coerceIn(1, half - 1)
            val hi = logBase.pow((b + 1.0) / BAR_COUNT).toInt().coerceIn(lo + 1, half)
            var acc = 0f
            for (k in lo until hi) {
                val re = fft[2 * k].toFloat()
                val im = fft[2 * k + 1].toFloat()
                acc += hypot(re, im)
            }
            val mean = acc / (hi - lo)
            val db = 20f * log10(mean.coerceAtLeast(1e-3f))
            // ช่วง dB ที่พบเชิงประจักษ์ ≈ [-12, 34] → normalise เป็น [0,1]
            bins[b] = ((db + 12f) / 46f).coerceIn(0f, 1f)
        }
    }

    override fun magnitudes(out: FloatArray, timeSeconds: Float, isPlaying: Boolean) {
        if (active && isPlaying) bins.copyInto(out, endIndex = min(bins.size, out.size))
        else fallback.magnitudes(out, timeSeconds, isPlaying)
    }
}
