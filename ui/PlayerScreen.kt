package com.aurora.player.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aurora.player.core.*
import com.aurora.player.visual.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    state: PlayerUiState,
    palette: AuroraPalette,
    waveform: WaveformSource,
    controller: AudioController,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = state.current
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableFloatStateOf(0f) }

    Box(modifier.fillMaxSize()) {

        DynamicBackdrop(
            artworkModel = track?.artworkUri,
            palette = palette,
            isPlaying = state.isPlaying,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))
            IconButton(onClick = onCollapse, modifier = Modifier.align(Alignment.Start)) {
                Icon(Icons.Rounded.KeyboardArrowDown, null, tint = palette.onSurface)
            }

            Spacer(Modifier.weight(1f))

            VinylArtwork(
                artworkModel = track?.artworkUri,
                isPlaying = state.isPlaying,
                progress = if (scrubbing) scrubValue else state.progress,
                palette = palette,
                size = 300.dp
            )

            Spacer(Modifier.height(36.dp))

            // ── Metadata พร้อม transition เมื่อเปลี่ยนเพลง ──────────────
            AnimatedContent(
                targetState = track,
                transitionSpec = {
                    (slideInVertically { it / 3 } + fadeIn(tween(350)))
                        .togetherWith(slideOutVertically { -it / 3 } + fadeOut(tween(220)))
                        .using(SizeTransform(clip = false))
                },
                label = "metadata"
            ) { t ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = t?.title ?: "ไม่มีเพลงที่เลือก",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.onSurface,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = t?.displayArtist ?: "—",
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.onSurface.copy(alpha = 0.78f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = t?.displayAlbum ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.onSurface.copy(alpha = 0.52f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            WaveformVisualizer(
                source = waveform,
                isPlaying = state.isPlaying,
                color = palette.vibrant,
                accent = palette.accent,
                modifier = Modifier.fillMaxWidth().height(72.dp)
            )

            Spacer(Modifier.height(20.dp))

            // ── Seek bar ─────────────────────────────────────────────────
            Slider(
                value = if (scrubbing) scrubValue else state.progress,
                onValueChange = { scrubbing = true; scrubValue = it },
                onValueChangeFinished = {
                    controller.seekTo((scrubValue * state.durationMs).toLong())
                    scrubbing = false
                },
                colors = SliderDefaults.colors(
                    thumbColor = palette.accent,
                    activeTrackColor = palette.vibrant,
                    inactiveTrackColor = palette.onSurface.copy(alpha = 0.18f)
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(if (scrubbing) (scrubValue * state.durationMs).toLong() else state.positionMs),
                    color = palette.onSurface.copy(alpha = 0.7f), fontSize = 12.sp)
                Text(formatTime(state.durationMs),
                    color = palette.onSurface.copy(alpha = 0.7f), fontSize = 12.sp)
            }

            Spacer(Modifier.height(24.dp))

            // ── Transport controls ──────────────────────────────────────
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconToggle(Icons.Rounded.Shuffle, state.shuffle, palette) { controller.toggleShuffle() }
                IconButton(onClick = controller::previous, Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, null, tint = palette.onSurface,
                        modifier = Modifier.size(38.dp))
                }
                PlayPauseButton(state.isPlaying, palette) { controller.togglePlayPause() }
                IconButton(onClick = controller::next, Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipNext, null, tint = palette.onSurface,
                        modifier = Modifier.size(38.dp))
                }
                IconToggle(
                    when (state.repeat) {
                        RepeatMode.ONE -> Icons.Rounded.RepeatOne
                        else -> Icons.Rounded.Repeat
                    },
                    state.repeat != RepeatMode.OFF, palette
                ) { controller.cycleRepeat() }
            }

            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun PlayPauseButton(isPlaying: Boolean, palette: AuroraPalette, onClick: () -> Unit) {
    val scale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.94f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
        label = "pp"
    )
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier
            .size(76.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = palette.accent,
            contentColor = if (com.aurora.player.visual.PaletteExtractor
                    .relativeLuminance(palette.accent) > 0.45f) Color.Black else Color.White
        )
    ) {
        AnimatedContent(
            targetState = isPlaying,
            transitionSpec = {
                (scaleIn(spring(stiffness = Spring.StiffnessMedium)) + fadeIn())
                    .togetherWith(scaleOut(tween(120)) + fadeOut(tween(120)))
            },
            label = "ppIcon"
        ) { playing ->
            Icon(
                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (playing) "หยุดชั่วคราว" else "เล่น",
                modifier = Modifier.size(40.dp)
            )
        }
    }
}

@Composable
private fun IconToggle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean, palette: AuroraPalette, onClick: () -> Unit,
) {
    val tint by animateColorAsState(
        if (active) palette.accent else palette.onSurface.copy(alpha = 0.45f),
        label = "toggleTint"
    )
    IconButton(onClick = onClick) { Icon(icon, null, tint = tint) }
}

fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    val m = total / 60; val s = total % 60
    return "$m:${s.toString().padStart(2, '0')}"
}
