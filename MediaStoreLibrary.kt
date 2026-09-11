package com.aurora.player

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.aurora.player.core.MediaLibrary
import com.aurora.player.core.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaStoreLibrary(private val context: Context) : MediaLibrary {

    override suspend fun scan(): List<Track> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
        )
        val artBase = Uri.parse("content://media/external/audio/albumart")
        buildList {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 20000",
                null,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
            )?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val iTi = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val iAr = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val iAl = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val iAId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val iDu = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    add(
                        Track(
                            id = id.toString(),
                            title = c.getString(iTi) ?: "Unknown",
                            artist = c.getString(iAr).orEmpty().takeIf { it != "<unknown>" }.orEmpty(),
                            album = c.getString(iAl).orEmpty(),
                            durationMs = c.getLong(iDu),
                            uri = ContentUris.withAppendedId(
                                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                            ).toString(),
                            artworkUri = ContentUris.withAppendedId(artBase, c.getLong(iAId)).toString(),
                        )
                    )
                }
            }
        }
    }
}
