package com.aurora.player.core

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage
import java.io.File

actual suspend fun loadArtworkBitmap(uri: String, maxDimension: Int): ImageBitmap? =
    withContext(Dispatchers.IO) {
        runCatching {
            val file = File(uri)
            if (!file.exists()) return@runCatching null
            // Skia ถอดรหัสภายใน native heap ซึ่งไม่กระทบ JVM heap
            SkiaImage.makeFromEncoded(file.readBytes())
                .toComposeImageBitmap()
        }.getOrNull()
    }
