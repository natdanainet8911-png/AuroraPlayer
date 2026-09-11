package com.aurora.player.core

import androidx.compose.runtime.Immutable

enum class RepeatMode { OFF, ALL, ONE }

@Immutable
data class PlayerUiState(
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
    val error: String? = null,
) {
    val current: Track? get() = queue.getOrNull(currentIndex)
    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}
