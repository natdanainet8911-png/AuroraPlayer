package com.aurora.player

import androidx.compose.runtime.*
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.aurora.player.core.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

fun main() = application {
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    val controller = remember { JavaFxAudioController(scope) }

    val library = remember {
        FileSystemLibrary(
            listOf(
                File(System.getProperty("user.home"), "Music"),
                File(System.getProperty("user.home"), "Downloads"),
            )
        )
    }
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    LaunchedEffect(Unit) { tracks = library.scan() }

    Window(
        onCloseRequest = { controller.release(); exitApplication() },
        title = "Aurora Player",
        state = rememberWindowState(size = DpSize(440.dp, 860.dp)),
    ) {
        App(controller = controller, tracks = tracks)
    }
}
