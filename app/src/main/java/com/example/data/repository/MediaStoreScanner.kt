package com.example.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.data.local.CachedTrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class MediaScanResult(
    val tracks: List<CachedTrackEntity>,
    val sidecarLyricsByTrackId: Map<Long, String>
)

class MediaStoreScanner(private val context: Context) {

    private val albumArtBaseUri: Uri = Uri.parse("content://media/external/audio/albumart")

    suspend fun scanDeviceAudio(
        onProgress: (scanned: Int, total: Int) -> Unit = { _, _ -> }
    ): MediaScanResult = withContext(Dispatchers.IO) {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATA
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Audio.Media.RELATIVE_PATH)
            }
        }.toTypedArray()

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.DURATION} >= 3000"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        val discoveredTracks = mutableListOf<CachedTrackEntity>()
        val sidecarLyrics = mutableMapOf<Long, String>()

        context.contentResolver.query(
            collection,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val total = cursor.count
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
            val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
            } else {
                -1
            }

            var index = 0
            while (cursor.moveToNext()) {
                index++
                val id = cursor.getLong(idCol)
                val rawTitle = if (!cursor.isNull(titleCol)) cursor.getString(titleCol)?.trim() else null
                val rawArtist = if (!cursor.isNull(artistCol)) cursor.getString(artistCol)?.trim() else null
                val rawAlbum = if (!cursor.isNull(albumCol)) cursor.getString(albumCol)?.trim() else null
                val albumId = if (!cursor.isNull(albumIdCol)) cursor.getLong(albumIdCol) else 0L
                val durationMs = if (!cursor.isNull(durationCol)) cursor.getLong(durationCol) else 0L
                val dateAddedSec = if (!cursor.isNull(dateAddedCol)) cursor.getLong(dateAddedCol) else 0L
                val trackNum = if (!cursor.isNull(trackCol)) cursor.getInt(trackCol) else 0
                val year = if (!cursor.isNull(yearCol)) cursor.getInt(yearCol) else 0
                val sizeBytes = if (!cursor.isNull(sizeCol)) cursor.getLong(sizeCol) else 0L
                val rawDataPath = if (dataCol >= 0 && !cursor.isNull(dataCol)) cursor.getString(dataCol) else null
                val relPath = if (relPathCol >= 0 && !cursor.isNull(relPathCol)) cursor.getString(relPathCol) else null

                if (durationMs <= 500L && sizeBytes <= 0L) continue

                val cleanTitle = when {
                    !rawTitle.isNullOrBlank() && rawTitle != "<unknown>" -> rawTitle
                    !rawDataPath.isNullOrBlank() -> File(rawDataPath).nameWithoutExtension
                    else -> "Untitled Track #$id"
                }

                val cleanArtist = when {
                    !rawArtist.isNullOrBlank() && rawArtist != "<unknown>" -> rawArtist
                    else -> "Unknown Artist"
                }

                val cleanAlbum = when {
                    !rawAlbum.isNullOrBlank() && rawAlbum != "<unknown>" -> rawAlbum
                    else -> "Unknown Album"
                }

                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id
                ).toString()

                val artworkUri = if (albumId > 0L) {
                    ContentUris.withAppendedId(albumArtBaseUri, albumId).toString()
                } else {
                    null
                }

                val folderPath = relPath ?: rawDataPath?.let { File(it).parent ?: "" } ?: ""

                // Check if an accessible sidecar .lrc file exists next to the audio file
                if (!rawDataPath.isNullOrBlank()) {
                    runCatching {
                        val audioFile = File(rawDataPath)
                        val lrcFile = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.lrc")
                        if (lrcFile.exists() && lrcFile.canRead() && lrcFile.length() in 1..200_000) {
                            sidecarLyrics[id] = lrcFile.readText()
                        }
                    }
                }

                discoveredTracks.add(
                    CachedTrackEntity(
                        id = id,
                        title = cleanTitle,
                        artist = cleanArtist,
                        album = cleanAlbum,
                        albumId = albumId,
                        durationMs = durationMs.coerceAtLeast(1000L),
                        contentUri = contentUri,
                        albumArtUri = artworkUri,
                        dateAddedSec = dateAddedSec,
                        trackNumber = trackNum,
                        year = year,
                        sizeBytes = sizeBytes,
                        folderPath = folderPath,
                        isAvailable = true,
                        isDemoTrack = false
                    )
                )

                if (index % 25 == 0 || index == total) {
                    onProgress(index, total)
                }
            }
        }

        MediaScanResult(
            tracks = discoveredTracks,
            sidecarLyricsByTrackId = sidecarLyrics
        )
    }

    suspend fun isTrackUriAccessible(contentUriString: String): Boolean = withContext(Dispatchers.IO) {
        if (contentUriString.startsWith("file://")) {
            val path = Uri.parse(contentUriString).path ?: return@withContext false
            return@withContext File(path).exists()
        }
        runCatching {
            val uri = Uri.parse(contentUriString)
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
        }.getOrDefault(false)
    }
}
