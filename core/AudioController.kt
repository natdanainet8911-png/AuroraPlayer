package com.aurora.player.core

import kotlinx.coroutines.flow.StateFlow

/**
 * สัญญาการควบคุมการเล่นเสียงที่เป็นกลางต่อแพลตฟอร์ม (Dependency Inversion Principle)
 * UI Layer พึ่งพา abstraction นี้เท่านั้น ไม่พึ่งพา ExoPlayer หรือ JavaFX โดยตรง
 */
interface AudioController {
    val state: StateFlow<PlayerUiState>
    val waveform: WaveformSource

    fun setQueue(tracks: List<Track>, startIndex: Int = 0, playWhenReady: Boolean = true)
    fun play()
    fun pause()
    fun togglePlayPause()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun seekToIndex(index: Int)
    fun toggleShuffle()
    fun cycleRepeat()
    fun release()
}

interface MediaLibrary { suspend fun scan(): List<Track> }
