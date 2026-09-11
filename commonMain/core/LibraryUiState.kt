package com.aurora.player.core

import androidx.compose.runtime.Immutable

/**
 * สถานะของคลังเพลง ออกแบบเป็น sealed hierarchy เพื่อให้ UI จัดการได้ครบทุกกรณี
 * (exhaustive when) ซึ่งเป็นการบังคับความครบถ้วนตั้งแต่ compile time
 */
@Immutable
sealed interface LibraryUiState {
    data object Loading : LibraryUiState
    data object PermissionRequired : LibraryUiState
    data object PermissionPermanentlyDenied : LibraryUiState
    data object Empty : LibraryUiState
    data class Ready(val tracks: List<Track>) : LibraryUiState
    data class Failed(val reason: String) : LibraryUiState

    val tracksOrEmpty: List<Track>
        get() = (this as? Ready)?.tracks ?: emptyList()
}
