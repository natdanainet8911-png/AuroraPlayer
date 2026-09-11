package com.aurora.player

import androidx.compose.runtime.*
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.aurora.player.core.LibraryUiState
import kotlinx.coroutines.*
import java.io.File

fun main() = application {
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    val controller = remember { JavaFxAudioController(scope) }

    val library = remember {
        FileSystemLibrary(
            listOfNotNull(
                File(System.getProperty("user.home"), "Music").takeIf { it.exists() },
                File(System.getProperty("user.home"), "Downloads").takeIf { it.exists() },
            )
        )
    }

    var libraryState by remember { mutableStateOf<LibraryUiState>(LibraryUiState.Loading) }

    val rescan: () -> Unit = {
        libraryState = LibraryUiState.Loading
        scope.launch {
            libraryState = runCatching { library.scan() }.fold(
                onSuccess = { if (it.isEmpty()) LibraryUiState.Empty else LibraryUiState.Ready(it) },
                onFailure = { LibraryUiState.Failed(it.message ?: "สแกนไม่สำเร็จ") }
            )
        }
    }

    LaunchedEffect(Unit) { rescan() }

    Window(
        onCloseRequest = {
            controller.release()
            scope.cancel()
            exitApplication()
        },
        title = "Aurora Player",
        state = rememberWindowState(size = DpSize(460.dp, 880.dp)),
    ) {
        App(
            controller = controller,
            libraryState = libraryState,
            onRequestPermission = { /* เดสก์ท็อปไม่ต้องขอสิทธิ์ */ },
            onRescan = rescan
        )
    }
}
