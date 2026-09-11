package com.aurora.player

import android.app.Application
import android.os.StrictMode
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.crossfade
import coil3.util.DebugLogger
import okio.Path.Companion.toOkioPath

/**
 * Application class ทำหน้าที่สองประการ
 *  1) กำหนดค่า ImageLoader ระดับ singleton (LRU memory cache + disk cache)
 *     เพื่อรับประกันว่าการโหลดปกอัลบั้มจะไม่เกิด decode ซ้ำ ซึ่งเป็นสาเหตุหลักของ jank
 *  2) เปิด StrictMode ในโหมด debug เพื่อตรวจจับ disk/network I/O บน Main Thread
 */
class AuroraApp : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
    super.onCreate()
    com.aurora.player.core.AndroidPlatform.appContext = applicationContext   // ← เพิ่ม
    if (BuildConfigCompat.DEBUG) enableStrictMode()
}


    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.20)   // ~20% ของ heap ที่ระบบจัดสรรให้
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_artwork").toOkioPath())
                    .maxSizeBytes(96L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .apply { if (BuildConfigCompat.DEBUG) logger(DebugLogger()) }
            .build()

    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads().detectDiskWrites().detectNetwork()
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectLeakedSqlLiteObjects()
                .penaltyLog()
                .build()
        )
    }
}

/** ปิด buildConfig ไว้ใน gradle.properties จึงใช้ตัวตรวจจับแบบเบาแทน */
internal object BuildConfigCompat {
    val DEBUG: Boolean by lazy {
        runCatching { Class.forName("com.aurora.player.BuildConfig") }.isSuccess ||
            System.getProperty("aurora.debug") == "true"
    }
}
