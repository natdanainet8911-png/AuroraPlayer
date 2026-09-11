package com.aurora.player.core

import androidx.compose.runtime.Immutable

@Immutable
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uri: String,
    /** โมเดลที่ Coil resolve ได้: content:// (Android) หรือ absolute path (Desktop) */
    val artworkUri: String? = null,
) {
    val displayArtist: String get() = artist.ifBlank { "Unknown Artist" }
    val displayAlbum: String get() = album.ifBlank { "Unknown Album" }
}
