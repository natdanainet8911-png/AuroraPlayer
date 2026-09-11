package com.aurora.player.visual

import androidx.compose.runtime.*
import com.aurora.player.core.loadArtworkBitmap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * LRU cache ขนาดคงที่สำหรับผลลัพธ์ palette
 * เหตุผล: การสกัดสีมี complexity O(N) และเกิดขึ้นทุกครั้งที่เปลี่ยนเพลง
 *         การ cache จึงตัดงานซ้ำในกรณีย้อนกลับ/วนเพลย์ลิสต์ออกทั้งหมด
 */
object PaletteCache {
    private const val CAPACITY = 48
    private val map = LinkedHashMap<String, AuroraPalette>(CAPACITY, 0.75f, true)
    private val mutex = Mutex()

    suspend fun get(uri: String?): AuroraPalette {
        if (uri.isNullOrBlank()) return AuroraPalette.Default
        mutex.withLock { map[uri] }?.let { return it }

        val bitmap = loadArtworkBitmap(uri, maxDimension = 128)
            ?: return AuroraPalette.Default
        val palette = PaletteExtractor.extract(bitmap)

        mutex.withLock {
            map[uri] = palette
            if (map.size > CAPACITY) {
                val eldest = map.keys.firstOrNull()
                if (eldest != null) map.remove(eldest)
            }
        }
        return palette
    }
}

/**
 * Composable helper — คืน palette ที่ผูกกับ artwork ปัจจุบัน
 * พร้อมกลไกป้องกัน race condition เมื่อผู้ใช้เปลี่ยนเพลงเร็วกว่าการคำนวณ
 */
@Composable
fun rememberPalette(artworkUri: String?): AuroraPalette {
    var palette by remember { mutableStateOf(AuroraPalette.Default) }
    LaunchedEffect(artworkUri) {
        palette = PaletteCache.get(artworkUri)   // LaunchedEffect ยกเลิกงานเก่าอัตโนมัติ
    }
    return palette
}
