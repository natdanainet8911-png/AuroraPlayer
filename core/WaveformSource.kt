package com.aurora.player.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

const val BAR_COUNT = 48

/** แหล่งข้อมูลสเปกตรัม: ค่าปกติ [0f,1f] ต่อแถบความถี่ */
interface WaveformSource {
    /** เรียกได้ทุกเฟรม ต้องมี complexity O(n) และห้าม allocate */
    fun magnitudes(out: FloatArray, timeSeconds: Float, isPlaying: Boolean)
}

/**
 * Fallback เชิงกระบวนวิธี (Procedural) — ใช้ผลรวมของคลื่นไซน์หลายความถี่
 * ที่ไม่เป็นจำนวนเท่าของกันและกัน (incommensurable frequencies)
 * เพื่อสร้างรูปแบบกึ่งสุ่มที่ไม่ซ้ำรอบ พร้อม spectral envelope แบบ pink-noise
 */
class ProceduralWaveformSource : WaveformSource {
    private var energy = 0f
    override fun magnitudes(out: FloatArray, timeSeconds: Float, isPlaying: Boolean) {
        val target = if (isPlaying) 1f else 0f
        energy += (target - energy) * 0.06f          // envelope follower
        val n = out.size
        for (i in 0 until n) {
            val x = i.toFloat() / n
            // ซองความถี่: พลังงานลดลงตามความถี่สูง (~1/f)
            val envelope = exp(-2.2f * x) * 0.85f + 0.15f
            val a = sin(timeSeconds * 2.31f + x * 11.0f)
            val b = sin(timeSeconds * 3.77f + x * 19.0f + 1.7f)
            val c = sin(timeSeconds * 1.13f + x * 5.0f + 4.2f)
            val v = (a * 0.5f + b * 0.3f + c * 0.2f)
            out[i] = (abs(v) * envelope * energy).coerceIn(0f, 1f)
        }
    }
}
