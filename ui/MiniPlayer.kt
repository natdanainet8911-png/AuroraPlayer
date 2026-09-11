package com.aurora.player.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.aurora.player.core.AudioController
import com.aurora.player.core.PlayerUiState
import com.aurora.player.visual.AuroraPalette
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * แถบควบคุมย่อที่ยึดอยู่ก้าวจอ
 *
 * คุณลักษณะเชิงปฏิสัมพันธ์:
 *  - ปัดซ้าย/ขวา เพื่อข้ามเพลง (swipe gesture ตามแนวทาง Material Motion)
 *  - แถบความคืบหน้าวาดผ่าน drawBehind{} → Draw Phase เท่านั้น ไม่กระทบ layout
 *  - ปกอัลบั้มหมุนช้าเมื่อเล่นอยู่ เพื่อสื่อสถานะโดยไม่ต้องใช้ข้อความ
 */
@Composable
fun MiniPlayer(
    state: PlayerUiState,
    palette: AuroraPalette,
    controller: AudioController,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = state.current ?: return

    var dragOffset by remember { mutableFloatStateOf(0f) }
    val animatedOffset by animateFloatAsState(
        targetValue = dragOffset,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
        label = "swipe"
    )

    val infinite = rememberInfiniteTransition(label = "miniSpin")
    val spin by infinite.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(18_000, easing = LinearEasing), RepeatMode.Restart),
        label = "spin"
    )
    val spinFactor by animateFloatAsState(
        if (state.isPlaying) 1f else 0f, tween(900), label = "spinFactor"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .height(68.dp)
            .graphicsLayer { translationX = animatedOffset }
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onExpand)
            .pointerInput(track.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        when {
                            dragOffset < -120f -> controller.next()
                            dragOffset > 120f  -> controller.previous()
                        }
                        dragOffset = 0f
                    },
                    onDragCancel = { dragOffset = 0f },
                    onHorizontalDrag = { _, delta ->
                        // Rubber-band resistance: ต้านทานเพิ่มขึ้นตามระยะ
                        val resistance = 1f - (abs(dragOffset) / 400f).coerceIn(0f, 0.75f)
                        dragOffset += delta * resistance
                    }
                )
            },
        color = palette.dominant.copy(alpha = 0.88f),
        tonalElevation = 8.dp,
        shadowElevation = 12.dp,
    ) {
        Box {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = track.artworkUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .graphicsLayer {
                            rotationZ = spin * spinFactor
                            shape = androidx.compose.foundation.shape.CircleShape
                            clip = true
                        }
                )
                Spacer(Modifier.width(12.dp))

                AnimatedContent(
                    targetState = track,
                    transitionSpec = {
                        (slideInHorizontally { it / 4 } + fadeIn(tween(250)))
                            .togetherWith(slideOutHorizontally { -it / 4 } + fadeOut(tween(150)))
                    },
                    modifier = Modifier.weight(1f),
                    label = "miniMeta"
                ) { t ->
                    Column {
                        Text(
                            t.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.onSurface,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            t.displayArtist,
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.onSurface.copy(alpha = 0.62f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(onClick = controller::previous) {
                    Icon(Icons.Rounded.SkipPrevious, "เพลงก่อนหน้า",
                        tint = palette.onSurface.copy(alpha = 0.8f))
                }
                MiniPlayPause(state.isPlaying, palette) { controller.togglePlayPause() }
                IconButton(onClick = controller::next) {
                    Icon(Icons.Rounded.SkipNext, "เพลงถัดไป",
                        tint = palette.onSurface.copy(alpha = 0.8f))
                }
            }

            // แถบความคืบหน้าด้านล่าง — วาดใน Draw Phase เท่านั้น
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .drawBehind {
                        drawRect(palette.onSurface.copy(alpha = 0.12f))
                        drawRect(
                            brush = Brush.horizontalGradient(
                                listOf(palette.accent, palette.vibrant)
                            ),
                            size = size.copy(width = size.width * state.progress)
                        )
                    }
            )
        }
    }
}

@Composable
private fun MiniPlayPause(isPlaying: Boolean, palette: AuroraPalette, onClick: () -> Unit) {
    val scale by animateFloatAsState(
        if (isPlaying) 1f else 0.92f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "miniPP"
    )
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp).graphicsLayer { scaleX = scale; scaleY = scale },
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = palette.accent.copy(alpha = 0.92f)
        )
    ) {
        AnimatedContent(
            targetState = isPlaying,
            transitionSpec = {
                (scaleIn(spring(stiffness = Spring.StiffnessMedium)) + fadeIn())
                    .togetherWith(scaleOut(tween(110)) + fadeOut(tween(110)))
            },
            label = "miniPPIcon"
        ) { playing ->
            Icon(
                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (playing) "หยุดชั่วคราว" else "เล่น",
                tint = Color.Black.copy(alpha = 0.85f)
            )
        }
    }
}
