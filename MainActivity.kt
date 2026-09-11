package com.aurora.player

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import com.aurora.player.core.Track
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val permissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* ผลลัพธ์ถูกอ่านผ่าน state ใน App() */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        permissions.launch(
            buildList {
                if (Build.VERSION.SDK_INT >= 33) {
                    add(android.Manifest.permission.READ_MEDIA_AUDIO)
                    add(android.Manifest.permission.POST_NOTIFICATIONS)
                } else add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                add(android.Manifest.permission.RECORD_AUDIO)
            }.toTypedArray()
        )

        setContent {
            val controller = rememberAudioController()
            val library = remember { MediaStoreLibrary(applicationContext) }
            var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
            val scope = rememberCoroutineScope()

            LaunchedEffect(Unit) {
                scope.launch { tracks = library.scan() }
            }
            App(controller = controller, tracks = tracks)
        }
    }
}
