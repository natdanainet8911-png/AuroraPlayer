package com.aurora.player

import com.aurora.player.core.*
import javafx.application.Platform
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.util.Duration
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * เอนจินเสียงฝั่งเดสก์ท็อป ใช้ JavaFX Media (รองรับ MP3/WAV/AAC/AIFF)
 * เหตุผลเชิงสถาปัตยกรรม: JavaFX bundle ได้ผ่าน jpackage โดยไม่ต้องติดตั้ง native
 * runtime เพิ่มเติม ต่างจาก VLCJ ที่ผูกกับ libvlc ของระบบ
 *
 * AudioSpectrumListener ให้ข้อมูลสเปกตรัมโดยตรง (dB ต่อ band)
 * จึงไม่ต้องคำนวณ FFT เอง
 */
class JavaFxAudioController(private val scope: CoroutineScope) : AudioController, WaveformSource {

    private val _state = MutableStateFlow(PlayerUiState())
    override val state: StateFlow<PlayerUiState> = _state.asStateFlow()
    override val waveform: WaveformSource get() = this

    private var player: MediaPlayer? = null
    private val bands = FloatArray(BAR_COUNT)
    private val fallback = ProceduralWaveformSource()
    private val hasSpectrum = AtomicBoolean(false)
    private var pollJob: Job? = null

    companion object {
        private val fxStarted = AtomicBoolean(false)
        fun ensureFxRuntime() {
            if (fxStarted.compareAndSet(false, true)) {
                Platform.startup { }              // เริ่ม JavaFX toolkit แบบ headless
                Platform.setImplicitExit(false)
            }
        }
    }

    init { ensureFxRuntime() }

    // -------------------------------------------------------------- Queue
    override fun setQueue(tracks: List<Track>, startIndex: Int, playWhenReady: Boolean) {
        _state.value = _state.value.copy(queue = tracks, currentIndex = startIndex)
        loadCurrent(playWhenReady)
    }

    private fun loadCurrent(autoPlay: Boolean) {
        val track = _state.value.current ?: return
        Platform.runLater {
            player?.dispose()
            val media = Media(File(track.uri).toURI().toString())
            player = MediaPlayer(media).apply {
                audioSpectrumNumBands = BAR_COUNT
                audioSpectrumInterval = 0.05
                audioSpectrumThreshold = -70
                setAudioSpectrumListener { _, _, magnitudes, _ ->
                    // JavaFX คืนค่าเป็น dB ในช่วง [threshold, 0]
                    for (i in bands.indices) {
                        val db = magnitudes.getOrElse(i) { -70f }
                        bands[i] = ((db + 70f) / 70f).coerceIn(0f, 1f)
                    }
                    hasSpectrum.set(true)
                }
                setOnReady {
                    _state.value = _state.value.copy(durationMs = media.duration.toMillis().toLong())
                }
                setOnEndOfMedia { this@JavaFxAudioController.next() }
                setOnPlaying  { _state.value = _state.value.copy(isPlaying = true);  startPolling() }
                setOnPaused   { _state.value = _state.value.copy(isPlaying = false); stopPolling() }
                setOnStopped  { _state.value = _state.value.copy(isPlaying = false); stopPolling() }
                setOnError    { _state.value = _state.value.copy(error = error?.message) }
                if (autoPlay) play()
            }
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                player?.let { p ->
                    _state.value = _state.value.copy(
                        positionMs = p.currentTime?.toMillis()?.toLong() ?: 0L
                    )
                }
                delay(200)
            }
        }
    }
    private fun stopPolling() { pollJob?.cancel(); pollJob = null }

    // -------------------------------------------------------------- Commands
    override fun play() = Platform.runLater { player?.play() }
    override fun pause() = Platform.runLater { player?.pause() }
    override fun togglePlayPause() {
        if (_state.value.isPlaying) pause() else play()
    }
    override fun next() {
        val s = _state.value
        if (s.queue.isEmpty()) return
        val idx = when (s.repeat) {
            RepeatMode.ONE -> s.currentIndex
            else -> (s.currentIndex + 1).let { if (it >= s.queue.size) 0 else it }
        }
        _state.value = s.copy(currentIndex = idx, positionMs = 0)
        loadCurrent(autoPlay = true)
    }
    override fun previous() {
        val s = _state.value
        if (s.positionMs > 3000) { seekTo(0); return }
        val idx = (s.currentIndex - 1).let { if (it < 0) s.queue.lastIndex else it }
        _state.value = s.copy(currentIndex = idx, positionMs = 0)
        loadCurrent(autoPlay = true)
    }
    override fun seekTo(positionMs: Long) {
        Platform.runLater { player?.seek(Duration.millis(positionMs.toDouble())) }
        _state.value = _state.value.copy(positionMs = positionMs)
    }
    override fun seekToIndex(index: Int) {
        _state.value = _state.value.copy(currentIndex = index, positionMs = 0)
        loadCurrent(true)
    }
    override fun toggleShuffle() { _state.value = _state.value.copy(shuffle = !_state.value.shuffle) }
    override fun cycleRepeat() {
        _state.value = _state.value.copy(
            repeat = when (_state.value.repeat) {
                RepeatMode.OFF -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.OFF
            }
        )
    }
    override fun release() {
        stopPolling()
        Platform.runLater { player?.dispose(); player = null }
    }

    // -------------------------------------------------------------- Waveform
    override fun magnitudes(out: FloatArray, timeSeconds: Float, isPlaying: Boolean) {
        if (hasSpectrum.get() && isPlaying) bands.copyInto(out)
        else fallback.magnitudes(out, timeSeconds, isPlaying)
    }
}
