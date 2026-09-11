package com.aurora.player

import android.content.ComponentName
import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.aurora.player.core.*
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val POSITION_POLL_MS = 200L   // Throttle: 5 Hz เพียงพอต่อการรับรู้ของมนุษย์

@OptIn(UnstableApi::class)
class Media3AudioController(
    private val appContext: Context,
    private val scope: CoroutineScope,
) : AudioController {

    private val _state = MutableStateFlow(PlayerUiState())
    override val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val fftSource = FftWaveformSource()
    override val waveform: WaveformSource get() = fftSource

    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var pollJob: Job? = null

    // ------------------------------------------------------------------ Listener
    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            val relevant = events.containsAny(
                Player.EVENT_IS_PLAYING_CHANGED,
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_MEDIA_METADATA_CHANGED,
                Player.EVENT_TIMELINE_CHANGED,
                Player.EVENT_POSITION_DISCONTINUITY,
                Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                Player.EVENT_REPEAT_MODE_CHANGED,
            )
            if (relevant) {
                syncSnapshot(player)
                managePolling(player.isPlaying)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.update { it.copy(error = error.errorCodeName, isPlaying = false) }
        }
    }

    // ------------------------------------------------------------------ Lifecycle
    fun connect() {
        if (future != null) return
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val f = MediaController.Builder(appContext, token).buildAsync()
        future = f
        f.addListener({
            runCatching { f.get() }.onSuccess { c ->
                controller = c
                c.addListener(playerListener)
                syncSnapshot(c)
                managePolling(c.isPlaying)
                fftSource.attach(c)          // ผูก Visualizer กับ audioSessionId
            }
        }, MoreExecutors.directExecutor())
    }

    /** เรียกเมื่อ UI เข้าสู่ STOPPED — ตัดการเชื่อมต่อ แต่ Service ยังเล่นต่อ */
    fun disconnect() {
        pollJob?.cancel(); pollJob = null
        fftSource.detach()
        controller?.removeListener(playerListener)
        controller = null
        future?.let { MediaController.releaseFuture(it) }
        future = null
    }

    override fun release() = disconnect()

    // ------------------------------------------------------------------ State sync
    private fun syncSnapshot(p: Player) {
        val queue = buildList {
            for (i in 0 until p.mediaItemCount) add(p.getMediaItemAt(i).toTrack())
        }
        _state.update { prev ->
            prev.copy(
                queue = queue,
                currentIndex = p.currentMediaItemIndex.takeIf { queue.isNotEmpty() } ?: -1,
                isPlaying = p.isPlaying,
                isBuffering = p.playbackState == Player.STATE_BUFFERING,
                positionMs = p.currentPosition.coerceAtLeast(0L),
                durationMs = p.duration.takeIf { it != C.TIME_UNSET } ?: 0L,
                shuffle = p.shuffleModeEnabled,
                repeat = when (p.repeatMode) {
                    Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                    Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                    else -> RepeatMode.OFF
                },
                error = null,
            )
        }
    }

    /** Polling เฉพาะตอนเล่นจริง — ลด wakeup ที่ไม่จำเป็น ประหยัดแบตและ CPU */
    private fun managePolling(isPlaying: Boolean) {
        pollJob?.cancel()
        if (!isPlaying) return
        pollJob = scope.launch {
            while (isActive) {
                controller?.let { c ->
                    _state.update {
                        it.copy(
                            positionMs = c.currentPosition.coerceAtLeast(0L),
                            durationMs = c.duration.takeIf { d -> d != C.TIME_UNSET } ?: it.durationMs
                        )
                    }
                }
                delay(POSITION_POLL_MS)
            }
        }
    }

    // ------------------------------------------------------------------ Commands
    override fun setQueue(tracks: List<Track>, startIndex: Int, playWhenReady: Boolean) {
        val c = controller ?: return
        c.setMediaItems(tracks.map { it.toMediaItem() }, startIndex, 0L)
        c.prepare()
        c.playWhenReady = playWhenReady
    }

    override fun play() { controller?.play() }
    override fun pause() { controller?.pause() }
    override fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else { c.prepare(); c.play() }
    }
    override fun next() { controller?.seekToNextMediaItem() }
    override fun previous() {
        val c = controller ?: return
        if (c.currentPosition > 3_000) c.seekTo(0) else c.seekToPreviousMediaItem()
    }
    override fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        _state.update { it.copy(positionMs = positionMs) }   // optimistic update → UI ตอบสนองทันที
    }
    override fun seekToIndex(index: Int) { controller?.seekTo(index, 0L) }
    override fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    override fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }
}

// ---------------------------------------------------------------------- Mappers
private fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setUri(uri)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(displayArtist)
            .setAlbumTitle(displayAlbum)
            .setArtworkUri(artworkUri?.let { android.net.Uri.parse(it) })
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .build()
    ).build()

private fun MediaItem.toTrack(): Track = Track(
    id = mediaId,
    title = mediaMetadata.title?.toString() ?: "Unknown",
    artist = mediaMetadata.artist?.toString() ?: "",
    album = mediaMetadata.albumTitle?.toString() ?: "",
    durationMs = mediaMetadata.durationMs ?: 0L,
    uri = localConfiguration?.uri?.toString() ?: "",
    artworkUri = mediaMetadata.artworkUri?.toString(),
)

private inline fun <T> MutableStateFlow<T>.update(fn: (T) -> T) { value = fn(value) }

// ---------------------------------------------------------------------- Compose binding
/**
 * ผูก MediaController เข้ากับ Composition อย่างปลอดภัยต่อ Lifecycle
 *  - ON_START → connect()   : UI กลับมามองเห็น จึงเชื่อมต่อ
 *  - ON_STOP  → disconnect(): UI หายไป ตัดการเชื่อมต่อ แต่ Service เล่นต่อเนื่อง
 */
@Composable
fun rememberAudioController(): AudioController {
    val context = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val controller = remember { Media3AudioController(context, scope) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> controller.connect()
                Lifecycle.Event.ON_STOP  -> controller.disconnect()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.release()
        }
    }
    return controller
}
