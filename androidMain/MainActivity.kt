package com.aurora.player

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.aurora.player.core.LibraryUiState
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requiredPermissions: Array<String> = buildList {
        if (Build.VERSION.SDK_INT >= 33) {
            add(Manifest.permission.READ_MEDIA_AUDIO)
            add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        add(Manifest.permission.RECORD_AUDIO)   // optional — ใช้กับ visualizer เท่านั้น
    }.toTypedArray()

    private val audioPermission: String
        get() = if (Build.VERSION.SDK_INT >= 33)
            Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    private fun hasAudioAccess(): Boolean =
        ContextCompat.checkSelfPermission(this, audioPermission) == PackageManager.PERMISSION_GRANTED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val controller = rememberAudioController()
            val library = remember { MediaStoreLibrary(applicationContext) }
            val scope = rememberCoroutineScope()

            var libraryState by remember {
                mutableStateOf<LibraryUiState>(
                    if (hasAudioAccess()) LibraryUiState.Loading else LibraryUiState.PermissionRequired
                )
            }
            var askedOnce by rememberSaveable { mutableStateOf(false) }

            val scan: () -> Unit = {
                libraryState = LibraryUiState.Loading
                scope.launch {
                    libraryState = runCatching { library.scan() }
                        .fold(
                            onSuccess = { if (it.isEmpty()) LibraryUiState.Empty else LibraryUiState.Ready(it) },
                            onFailure = { LibraryUiState.Failed(it.message ?: "อ่านคลังเพลงไม่สำเร็จ") }
                        )
                }
            }

            val launcher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { result ->
                askedOnce = true
                val granted = result[audioPermission] == true
                libraryState = when {
                    granted -> LibraryUiState.Loading.also { scan() }
                    // ปฏิเสธแล้วระบบไม่ยอมแสดง dialog อีก → ต้องไปตั้งค่าเอง
                    !shouldShowRequestPermissionRationale(audioPermission) ->
                        LibraryUiState.PermissionPermanentlyDenied
                    else -> LibraryUiState.PermissionRequired
                }
            }

            // ขอสิทธิ์อัตโนมัติครั้งแรกเท่านั้น — หลังจากนั้นให้ผู้ใช้กดเอง
            LaunchedEffect(Unit) {
                if (hasAudioAccess()) scan()
                else if (!askedOnce) launcher.launch(requiredPermissions)
            }

            // ตรวจสอบซ้ำเมื่อกลับจากหน้า Settings
            DisposableEffect(Unit) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, e ->
                    if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME &&
                        hasAudioAccess() && libraryState !is LibraryUiState.Ready
                    ) scan()
                }
                lifecycle.addObserver(observer)
                onDispose { lifecycle.removeObserver(observer) }
            }

            App(
                controller = controller,
                libraryState = libraryState,
                onRequestPermission = {
                    if (libraryState is LibraryUiState.PermissionPermanentlyDenied) {
                        startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(Uri.fromParts("package", packageName, null))
                        )
                    } else launcher.launch(requiredPermissions)
                },
                onRescan = scan
            )
        }
    }
}
