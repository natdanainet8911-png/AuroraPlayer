package com.aurora.player.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.aurora.player.core.*
import com.aurora.player.visual.AuroraPalette
import com.aurora.player.visual.DynamicBackdrop

/**
 * หน้าคลังเพลง
 *
 * หลักการเชิงประสิทธิภาพที่ใช้:
 *  1. LazyColumn + stable key → Compose สามารถ reuse/reorder node ได้แทนการสร้างใหม่
 *  2. animateItem() → item animation ทำงานใน Layout/Draw Phase ไม่กระตุ้น recomposition
 *  3. derivedStateOf สำหรับผลการค้นหา → คำนวณใหม่เฉพาะเมื่อ input เปลี่ยนจริง
 *  4. contentType ระบุชนิด item → เพิ่มอัตราการ reuse ของ composition pool
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    libraryState: LibraryUiState,
    state: PlayerUiState,
    palette: AuroraPalette,
    controller: AudioController,
    onTrackClick: (List<Track>, Int) -> Unit,
    onExpandPlayer: () -> Unit,
    onRequestPermission: () -> Unit,
    onRescan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    val allTracks = libraryState.tracksOrEmpty
    val filtered by remember(allTracks) {
        derivedStateOf {
            if (query.isBlank()) allTracks
            else {
                val q = query.trim().lowercase()
                allTracks.filter {
                    it.title.lowercase().contains(q) ||
                        it.artist.lowercase().contains(q) ||
                        it.album.lowercase().contains(q)
                }
            }
        }
    }

    Box(modifier.fillMaxSize()) {

        DynamicBackdrop(
            artworkModel = state.current?.artworkUri,
            palette = palette,
            isPlaying = state.isPlaying,
            modifier = Modifier.fillMaxSize(),
            blurRadius = 96.dp
        )

        Column(Modifier.fillMaxSize().safeDrawingPadding()) {

            // ── Header ────────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "คลังเพลง",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.onSurface
                    )
                    AnimatedVisibility(allTracks.isNotEmpty()) {
                        Text(
                            "${allTracks.size} เพลง",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.onSurface.copy(alpha = 0.55f)
                        )
                    }
                }
                IconButton(onClick = onRescan) {
                    Icon(Icons.Rounded.Refresh, "สแกนใหม่", tint = palette.onSurface)
                }
                IconButton(onClick = {
                    if (allTracks.isNotEmpty()) {
                        controller.toggleShuffle()
                        onTrackClick(allTracks.shuffled(), 0)
                    }
                }) {
                    Icon(Icons.Rounded.Shuffle, "สุ่มเล่น", tint = palette.accent)
                }
            }

            // ── Search ────────────────────────────────────────────────────
            AnimatedVisibility(visible = allTracks.isNotEmpty()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    placeholder = { Text("ค้นหาเพลง ศิลปิน หรืออัลบั้ม") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = {
                        AnimatedVisibility(query.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Rounded.Close, "ล้าง")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.accent,
                        unfocusedBorderColor = palette.onSurface.copy(alpha = 0.22f),
                        focusedтатTextColor = palette.onSurface,
                        unfocusedTextColor = palette.onSurface,
                    )
                )
            }

            Spacer(Modifier.height(8.dp))

            // ── Content: exhaustive state handling ────────────────────────
            Box(Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = libraryState,
                    transitionSpec = { fadeIn(tween(280)).togetherWith(fadeOut(tween(180))) },
                    label = "libraryContent"
                ) { s ->
                    when (s) {
                        LibraryUiState.Loading -> LoadingState(palette)

                        LibraryUiState.PermissionRequired -> StatusState(
                            icon = Icons.Rounded.LibraryMusic,
                            title = "ต้องการสิทธิ์เข้าถึงไฟล์เพลง",
                            body = "แอปจำเป็นต้องอ่านไฟล์เสียงในเครื่อง เพื่อแสดงรายชื่อเพลง ปกอัลบั้ม และข้อมูลศิลปิน",
                            actionLabel = "อนุญาต",
                            onAction = onRequestPermission,
                            palette = palette
                        )

                        LibraryUiState.PermissionPermanentlyDenied -> StatusState(
                            icon = Icons.Rounded.Settings,
                            title = "สิทธิ์ถูกปฏิเสธถาวร",
                            body = "กรุณาเปิดสิทธิ์ “เพลงและเสียง” ด้วยตนเองในหน้าการตั้งค่าแอป",
                            actionLabel = "ไปที่การตั้งค่า",
                            onAction = onRequestPermission,
                            palette = palette
                        )

                        LibraryUiState.Empty -> StatusState(
                            icon = Icons.Rounded.MusicOff,
                            title = "ไม่พบไฟล์เพลง",
                            body = "ยังไม่พบไฟล์เสียงในเครื่อง ลองเพิ่มไฟล์ลงโฟลเดอร์ Music แล้วกดสแกนใหม่",
                            actionLabel = "สแกนใหม่",
                            onAction = onRescan,
                            palette = palette
                        )

                        is LibraryUiState.Failed -> StatusState(
                            icon = Icons.Rounded.ErrorOutline,
                            title = "เกิดข้อผิดพลาด",
                            body = s.reason,
                            actionLabel = "ลองอีกครั้ง",
                            onAction = onRescan,
                            palette = palette
                        )

                        is LibraryUiState.Ready -> {
                            if (filtered.isEmpty()) {
                                StatusState(
                                    icon = Icons.Rounded.SearchOff,
                                    title = "ไม่พบผลลัพธ์",
                                    body = "ไม่พบเพลงที่ตรงกับ “$query”",
                                    actionLabel = "ล้างการค้นหา",
                                    onAction = { query = "" },
                                    palette = palette
                                )
                            } else {
                                LazyColumn(
                                    state = listState,
                                    contentPadding = PaddingValues(
                                        start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(
                                        items = filtered,
                                        key = { it.id },              // stable key → reuse node
                                        contentType = { "track" }     // เพิ่มอัตรา reuse
                                    ) { track ->
                                        TrackRow(
                                            track = track,
                                            isCurrent = state.current?.id == track.id,
                                            isPlaying = state.isPlaying,
                                            palette = palette,
                                            onClick = {
                                                onTrackClick(filtered, filtered.indexOf(track))
                                            },
                                            modifier = Modifier.animateItem(
                                                fadeInSpec = tween(220),
                                                placementSpec = spring(
                                                    stiffness = Spring.StiffnessMediumLow,
                                                    visibilityThreshold = androidx.compose.ui.unit.IntOffset.VisibilityThreshold
                                                )
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Mini player ───────────────────────────────────────────────────
        AnimatedVisibility(
            visible = state.current != null,
            enter = slideInVertically { it } + fadeIn(tween(260)),
            exit = slideOutVertically { it } + fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
        ) {
            MiniPlayer(
                state = state,
                palette = palette,
                controller = controller,
                onExpand = onExpandPlayer
            )
        }
    }
}

// ───────────────────────────────────────────────────────────── Track row
@Composable
private fun TrackRow(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    palette: AuroraPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg by animateColorAsState(
        if (isCurrent) palette.accent.copy(alpha = 0.14f) else Color.Transparent,
        tween(300), label = "rowBg"
    )
    val titleColor by animateColorAsState(
        if (isCurrent) palette.accent else palette.onSurface,
        tween(300), label = "rowTitle"
    )

    Surface(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        color = bg
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = track.artworkUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp))
                )
                AnimatedVisibility(
                    visible = isCurrent && isPlaying,
                    enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()
                ) {
                    Box(
                        Modifier.size(52.dp).clip(RoundedCornerShape(12.dp))
                            .graphicsLayer { alpha = 0.6f },
                        contentAlignment = Alignment.Center
                    ) {
                        EqualizerIndicator(palette.accent)
                    }
                }
            }

            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                    color = titleColor,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${track.displayArtist} • ${track.displayAlbum}",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.onSurface.copy(alpha = 0.55f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                formatTime(track.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = palette.onSurface.copy(alpha = 0.42f)
            )
        }
    }
}

/** ตัวบ่งชี้ "กำลังเล่น" แบบ 3 แท่ง — animation อิสระจาก state ภายนอก */
@Composable
private fun EqualizerIndicator(color: Color) {
    val infinite = rememberInfiniteTransition(label = "eq")
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(320, 460, 380).forEachIndexed { i, duration ->
            val h by infinite.animateFloat(
                initialValue = 0.28f, targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(duration, delayMillis = i * 90, easing = FastOutSlowInEasing),
                    RepeatMode.Reverse
                ), label = "bar$i"
            )
            Box(
                Modifier
                    .width(3.dp)
                    .height(20.dp)
                    .graphicsLayer { scaleY = h; transformOrigin =
                        androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f) }
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

// ───────────────────────────────────────────────────────── Status states
@Composable
private fun LoadingState(palette: AuroraPalette) {
    Column(
        Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = palette.accent, strokeWidth = 3.dp)
        Spacer(Modifier.height(16.dp))
        Text("กำลังสแกนคลังเพลง…",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.onSurface.copy(alpha = 0.7f))
    }
}

@Composable
private fun StatusState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
    palette: AuroraPalette,
) {
    Column(
        Modifier.fillMaxSize().padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = palette.accent.copy(alpha = 0.85f),
            modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold, color = palette.onSurface,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium,
            color = palette.onSurface.copy(alpha = 0.62f), textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onAction,
            colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
            shape = RoundedCornerShape(14.dp)
        ) { Text(actionLabel, color = Color.Black.copy(alpha = 0.85f)) }
    }
}
