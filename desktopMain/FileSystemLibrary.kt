package com.aurora.player

import com.aurora.player.core.MediaLibrary
import com.aurora.player.core.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import java.io.File

class FileSystemLibrary(private val roots: List<File>) : MediaLibrary {

    private val supported = setOf("mp3", "m4a", "aac", "wav", "aiff", "flac")
    private val artCacheDir: File =
        File(System.getProperty("java.io.tmpdir"), "aurora_art").apply { mkdirs() }

    override suspend fun scan(): List<Track> = withContext(Dispatchers.IO) {
        roots.asSequence()
            .filter { it.exists() }
            .flatMap { it.walkTopDown().maxDepth(6) }
            .filter { it.isFile && it.extension.lowercase() in supported }
            .mapNotNull { file -> runCatching { readTrack(file) }.getOrNull() }
            .sortedBy { it.title.lowercase() }
            .toList()
    }

    private fun readTrack(file: File): Track {
        val af = AudioFileIO.read(file)
        val tag = af.tag
        val artPath = tag?.firstArtwork?.binaryData?.let { bytes ->
            val out = File(artCacheDir, "${file.absolutePath.hashCode()}.img")
            if (!out.exists()) out.writeBytes(bytes)
            out.absolutePath
        }
        return Track(
            id = file.absolutePath,
            title = tag?.getFirst(org.jaudiotagger.tag.FieldKey.TITLE)
                ?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension,
            artist = tag?.getFirst(org.jaudiotagger.tag.FieldKey.ARTIST).orEmpty(),
            album = tag?.getFirst(org.jaudiotagger.tag.FieldKey.ALBUM).orEmpty(),
            durationMs = (af.audioHeader.trackLength * 1000L),
            uri = file.absolutePath,
            artworkUri = artPath,
        )
    }
}
