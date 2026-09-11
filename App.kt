package com.aurora.player

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import coil3.ImageLoader
import com.aurora.player.core.AudioController
import com.aurora.player.core.Track
import com.aurora.player.ui.LibraryScreen
import com.aurora.player.ui.PlayerScreen
import com.aurora.player.ui.AuroraTheme
import com.aurora.player.visual.AuroraPalette
import com.aurora.player.visual.PaletteExtractor

@Composable
fun App(controller: AudioController, tracks: List<Track>) {
    val state by controller.state.collectAsState()
    var expanded by rememberSaveable { mutableStateOf(false) }

    // ── สกัด Palette แบบ asynchronous ทุกครั้งที่เปลี่ยนปกอัลบั้ม ──────────
    val platformContext = LocalPlatformContext.current
    val loader = remember { ImageLoader(platformContext) }
    var palette by remember { mutableStateOf(AuroraPalette.Default) }

    LaunchedEffect(state.current?.artworkUri) {
        val art = state.current?.artworkUri ?: run { palette = AuroraPalette.Default; return@LaunchedEffect }
        val result = loader.execute(
            ImageRequest.Builder(platformContext).data(art).size(128).build()
        )
        if (result is SuccessResult) {
            val bmp: ImageBitmap = result.image.toBitmap().let {
                @Suppress("USELESS_CAST") it as ImageBitmap
            }
            palette = PaletteExtractor.extract(bmp)
        }
    }

    AuroraTheme(palette) {
        Surface(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = expanded,
                transitionSpec = {
                    if (targetState) {
                        (slideInVertically { it } + fadeIn(tween(280)))
                            .togetherWith(fadeOut(tween(200)))
                    } else {
                        fadeIn(tween(280))
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
                        tracks = tracks,
                        state = state,
                        palette = palette,
                        onTrackClick = { index -> controller.setQueue(tracks, index); expanded = true },
                        onMiniPlayerClick = { expanded = true },
                        controller = controller
                    )
                }
            }
        }
    }
}
