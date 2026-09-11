package com.aurora.player.core

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** ตัวชี้ Context ระดับแอป — ตั้งค่าครั้งเดียวใน AuroraApp.onCreate() */
object AndroidPlatform {
    lateinit var appContext: Context
        internal set
}

actual suspend fun loadArtworkBitmap(uri: String, maxDimension: Int): ImageBitmap? =
    withContext(Dispatchers.IO) {
        val resolver = AndroidPlatform.appContext.contentResolver
        val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return@withContext null

        // Pass 1: อ่านเฉพาะ header เพื่อทราบขนาดจริง (inJustDecodeBounds)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            resolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        }.getOrNull()
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

        // คำนวณ inSampleSize เป็นกำลังของสองที่ใหญ่ที่สุดซึ่งยังคง ≥ maxDimension
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxDimension &&
               bounds.outHeight / (sample * 2) >= maxDimension) {
            sample *= 2
        }

        // Pass 2: ถอดรหัสจริงด้วย ARGB_8888 (จำเป็นต่อการอ่าน pixel ที่แม่นยำ)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
            inMutable = false
        }
        runCatching {
            resolver.openInputStream(parsed)?.use {
                BitmapFactory.decodeStream(it, null, opts)?.asImageBitmap()
            }
        }.getOrNull()
    }
