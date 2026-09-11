package com.aurora.player

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.aurora.player.core.AudioController
import com.aurora.player.core.LibraryUiState
import com.aurora.player.core.Track
import com.aurora.player.ui.AuroraTheme
import com.aurora.player.ui.LibraryScreen
import com.aurora.player.ui.PlayerScreen
import com.aurora.player.visual.rememberPalette

/**
 * Composition root — เป็นจุดเดียวที่ประกอบ state, theme และ navigation เข้าด้วยกัน
 * ไม่มี business logic อยู่ที่นี่ ทำหน้าที่เพียง orchestration ตามหลัก
 * Single Responsibility Principle
 */
@Composable
fun App(
    controller: AudioController,
    libraryState: LibraryUiState,
    onRequestPermission: () -> Unit,
    onRescan: () -> Unit,
) {
    val state by controller.state.collectAsState()
    var expanded by rememberSaveable { mutableStateOf(false) }

    // Palette ถูกคำนวณแบบ async + cached → ไม่บล็อก Main Thread
    val palette = rememberPalette(state.current?.artworkUri)

    AuroraTheme(palette) {
        Surface(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = expanded,
                transitionSpec = {
                    if (targetState) {
                        (slideInVertically { it } + fadeIn(tween(300)))
                            .togetherWith(fadeOut(tween(200)) + scaleOut(targetScale = 0.94f))
                    } else {
                        (fadeIn(tween(300)) + scaleIn(initialScale = 0.96f))
                            .togetherWith(slideOutVertically { it } + fadeOut(tween(220)))
                    }.using(SizeTransform(clip = false))
                },
                label = "rootNav"
            ) { isExpanded ->
                if (isExpanded) {
                    PlayerScreen(
                        state = state,
                        palette = palette,
                        waveform = controller.waveform,
                        controller = controller,
                        onCollapse = { expanded = false }
                    )
                } else {
                    LibraryScreen(
                        libraryState = libraryState,
                        state = state,
                        palette = palette,
                        controller = controller,
                        onTrackClick = { list: List<Track>, index: Int ->
                            controller.setQueue(list, index)
                            expanded = true
                        },
                        onExpandPlayer = { expanded = true },
                        onRequestPermission = onRequestPermission,
                        onRescan = onRescan
                    )
                }
            }
        }
    }
}
