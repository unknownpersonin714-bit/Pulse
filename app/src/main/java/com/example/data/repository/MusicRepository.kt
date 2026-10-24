package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.local.CachedTrackEntity
import com.example.data.local.LyricsEntity
import com.example.data.local.PlayHistoryEntity
import com.example.data.local.PlaylistEntity
import com.example.data.local.PulseDao
import com.example.data.local.RecentSearchEntity
import com.example.domain.model.Album
import com.example.domain.model.Artist
import com.example.domain.model.ListeningStats
import com.example.domain.model.ParsedLyrics
import com.example.domain.model.PlaylistSummary
import com.example.domain.model.ScanProgress
import com.example.domain.model.Track
import com.example.util.DemoAudioSynthesizer
import com.example.util.LrcParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class MusicRepository(
    private val context: Context,
    private val dao: PulseDao,
    private val scanner: MediaStoreScanner
) {
    private val _scanProgress = MutableStateFlow(ScanProgress())
    val scanProgress: StateFlow<ScanProgress> = _scanProgress.asStateFlow()

    val tracksFlow: Flow<List<Track>> = combine(
        dao.observeAllCachedTracks(),
        dao.observeFavourites(),
        dao.observeTrackPlayStats()
    ) { cachedTracks, favourites, playStats ->
        val favIds = favourites.asSequence().map { it.trackId }.toHashSet()
        val statsByTrackId = playStats.associateBy { it.trackId }

        cachedTracks.map { entity ->
            val stat = statsByTrackId[entity.id]
            entity.toDomainTrack(
                isFavourite = favIds.contains(entity.id),
                playCount = stat?.playCount ?: 0,
                lastPlayedAt = stat?.lastPlayedAt
            )
        }
    }

    val favouritesFlow: Flow<List<Track>> = combine(
        tracksFlow,
        dao.observeFavourites()
    ) { tracks, favEntities ->
        val trackById = tracks.associateBy { it.id }
        favEntities.mapNotNull { trackById[it.trackId] }
    }

    val playlistsFlow: Flow<List<PlaylistSummary>> = combine(
        dao.observePlaylists(),
        dao.observeAllPlaylistCrossRefs(),
        tracksFlow
    ) { playlists, crossRefs, allTracks ->
        val trackMap = allTracks.associateBy { it.id }
        val refsByPlaylist = crossRefs.groupBy { it.playlistId }
        playlists.map { playlist ->
            val orderedTracks = refsByPlaylist[playlist.id]
                .orEmpty()
                .sortedBy { it.position }
                .mapNotNull { ref -> trackMap[ref.trackId] }
            PlaylistSummary(
                id = playlist.id,
                name = playlist.name,
                createdAt = playlist.createdAt,
                updatedAt = playlist.updatedAt,
                tracks = orderedTracks
            )
        }
    }

    val recentSearchesFlow: Flow<List<String>> = dao.observeRecentSearches()
        .map { list -> list.map { it.query } }

    val listeningStatsFlow: Flow<ListeningStats> = combine(
        tracksFlow,
        dao.observeTrackPlayStats()
    ) { tracks, stats ->
        val totalPlays = stats.sumOf { it.playCount }
        val totalDurationMs = stats.sumOf { it.totalDurationMs }
        val uniqueTracks = stats.count { it.playCount > 0 }
        val mostPlayed = tracks.filter { it.playCount > 0 }.maxByOrNull { it.playCount }
        val topArtist = tracks
            .filter { it.playCount > 0 }
            .groupBy { it.artist }
            .maxByOrNull { (_, artistTracks) -> artistTracks.sumOf { it.playCount } }
            ?.key

        ListeningStats(
            totalPlays = totalPlays,
            totalListeningTimeMs = totalDurationMs,
            uniqueTracksPlayed = uniqueTracks,
            mostPlayedTrack = mostPlayed,
            topArtist = topArtist
        )
    }

    fun groupAlbums(tracks: List<Track>): List<Album> {
        return tracks
            .groupBy { "${it.albumId}_${it.album.lowercase()}" }
            .values
            .map { albumTracks ->
                val representative = albumTracks.first()
                val sorted = albumTracks.sortedWith(
                    compareBy<Track> { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
                        .thenBy { it.title.lowercase() }
                )
                Album(
                    id = representative.albumId,
                    title = representative.album,
                    artist = representative.artist,
                    artworkUri = albumTracks.firstOrNull { !it.albumArtUri.isNullOrBlank() }?.albumArtUri,
                    trackCount = sorted.size,
                    totalDurationMs = sorted.sumOf { it.durationMs },
                    year = albumTracks.maxOfOrNull { it.year } ?: 0,
                    tracks = sorted
                )
            }
            .sortedBy { it.title.lowercase() }
    }

    fun groupArtists(tracks: List<Track>): List<Artist> {
        return tracks
            .groupBy { it.artist.trim().ifEmpty { "Unknown Artist" } }
            .entries
            .map { (artistName, artistTracks) ->
                val sorted = artistTracks.sortedBy { it.title.lowercase() }
                val distinctAlbums = artistTracks.map { it.album.lowercase() }.distinct().size
                Artist(
                    name = artistName,
                    trackCount = sorted.size,
                    albumCount = distinctAlbums,
                    totalDurationMs = sorted.sumOf { it.durationMs },
                    artworkUri = sorted.firstOrNull { !it.albumArtUri.isNullOrBlank() }?.albumArtUri,
                    tracks = sorted
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    suspend fun rescanMediaStore(): Result<Int> = withContext(Dispatchers.IO) {
        _scanProgress.value = ScanProgress(isScanning = true, scannedCount = 0, totalCount = 0, errorMessage = null)
        runCatching {
            val result = scanner.scanDeviceAudio { scanned, total ->
                _scanProgress.value = ScanProgress(
                    isScanning = true,
                    scannedCount = scanned,
                    totalCount = total
                )
            }

            if (result.tracks.isEmpty()) {
                dao.clearAllMediaStoreTracks()
            } else {
                dao.upsertCachedTracks(result.tracks)
                dao.removeMissingMediaStoreTracks(result.tracks.map { it.id })
            }

            // Store any sidecar .lrc lyrics discovered alongside local audio files
            for ((trackId, rawLrc) in result.sidecarLyricsByTrackId) {
                val existing = dao.getLyricsForTrackOnce(trackId)
                if (existing == null) {
                    val parsed = LrcParser.parse(trackId, rawLrc, "Sidecar .lrc")
                    dao.saveLyrics(
                        LyricsEntity(
                            trackId = trackId,
                            rawContent = rawLrc,
                            isSynced = parsed.isSynced,
                            sourceName = "Sidecar .lrc"
                        )
                    )
                }
            }

            _scanProgress.value = ScanProgress(
                isScanning = false,
                scannedCount = result.tracks.size,
                totalCount = result.tracks.size,
                lastScanTimestamp = System.currentTimeMillis()
            )
            result.tracks.size
        }.onFailure { e ->
            _scanProgress.value = ScanProgress(
                isScanning = false,
                errorMessage = e.localizedMessage ?: "Failed to scan device MediaStore"
            )
        }
    }

    suspend fun syncDemoModeTracks(enabled: Boolean) = withContext(Dispatchers.IO) {
        if (enabled) {
            val bundle = DemoAudioSynthesizer.generateSynthesizedDemoTracks(context)
            dao.upsertCachedTracks(bundle.tracks)
            for ((trackId, rawLrc) in bundle.lrcByTrackId) {
                val parsed = LrcParser.parse(trackId, rawLrc, "Acoustic Synth LRC")
                dao.saveLyrics(
                    LyricsEntity(
                        trackId = trackId,
                        rawContent = rawLrc,
                        isSynced = parsed.isSynced,
                        sourceName = "Acoustic Synth LRC"
                    )
                )
            }
        } else {
            dao.clearDemoTracks()
        }
    }

    suspend fun toggleFavourite(trackId: Long): Boolean = withContext(Dispatchers.IO) {
        dao.toggleFavourite(trackId)
    }

    suspend fun createPlaylist(name: String): Long = withContext(Dispatchers.IO) {
        val cleanName = name.trim().ifEmpty { "Untitled Playlist" }
        dao.insertPlaylist(PlaylistEntity(name = cleanName))
    }

    suspend fun renamePlaylist(playlistId: Long, newName: String) = withContext(Dispatchers.IO) {
        val cleanName = newName.trim()
        if (cleanName.isNotEmpty()) {
            dao.renamePlaylist(playlistId, cleanName)
        }
    }

    suspend fun deletePlaylist(playlistId: Long) = withContext(Dispatchers.IO) {
        dao.deletePlaylistWithTracks(playlistId)
    }

    suspend fun addTrackToPlaylist(playlistId: Long, trackId: Long) = withContext(Dispatchers.IO) {
        dao.addTrackToPlaylist(playlistId, trackId)
    }

    suspend fun addTracksToPlaylist(playlistId: Long, trackIds: List<Long>) = withContext(Dispatchers.IO) {
        for (id in trackIds) {
            dao.addTrackToPlaylist(playlistId, id)
        }
    }

    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) = withContext(Dispatchers.IO) {
        dao.removeTrackFromPlaylist(playlistId, trackId)
    }

    suspend fun reorderPlaylistTracks(playlistId: Long, orderedTrackIds: List<Long>) = withContext(Dispatchers.IO) {
        dao.reorderPlaylistTracks(playlistId, orderedTrackIds)
    }

    suspend fun recordTrackPlay(trackId: Long, listenedDurationMs: Long) = withContext(Dispatchers.IO) {
        dao.insertPlayHistory(
            PlayHistoryEntity(
                trackId = trackId,
                playedAt = System.currentTimeMillis(),
                listenedDurationMs = listenedDurationMs
            )
        )
    }

    suspend fun markTrackUnavailable(trackId: Long) = withContext(Dispatchers.IO) {
        dao.setTrackAvailability(trackId, false)
    }

    suspend fun verifyTrackAccessible(track: Track): Boolean {
        val accessible = scanner.isTrackUriAccessible(track.contentUri)
        if (!accessible) {
            markTrackUnavailable(track.id)
        }
        return accessible
    }

    fun observeLyricsForTrack(trackId: Long): Flow<ParsedLyrics?> {
        return dao.observeLyricsForTrack(trackId).map { entity ->
            if (entity == null || entity.rawContent.isBlank()) {
                null
            } else {
                LrcParser.parse(trackId, entity.rawContent, entity.sourceName)
            }
        }
    }

    suspend fun importLyricsFromUri(trackId: Long, uri: Uri): Result<ParsedLyrics> = withContext(Dispatchers.IO) {
        runCatching {
            val fileName = queryDisplayName(uri) ?: "Imported Lyrics"
            val rawText = context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader(Charsets.UTF_8).readText()
            } ?: throw IllegalArgumentException("Unable to read selected file.")

            if (rawText.isBlank()) {
                throw IllegalArgumentException("Selected lyrics file is empty.")
            }

            val parsed = LrcParser.parse(trackId, rawText, fileName)
            dao.saveLyrics(
                LyricsEntity(
                    trackId = trackId,
                    rawContent = rawText,
                    isSynced = parsed.isSynced,
                    sourceName = fileName
                )
            )
            parsed
        }
    }

    suspend fun saveCustomLyricsText(trackId: Long, rawContent: String, label: String = "Manual LRC/Text") =
        withContext(Dispatchers.IO) {
            val trimmed = rawContent.trim()
            if (trimmed.isEmpty()) {
                dao.deleteLyricsForTrack(trackId)
            } else {
                val parsed = LrcParser.parse(trackId, trimmed, label)
                dao.saveLyrics(
                    LyricsEntity(
                        trackId = trackId,
                        rawContent = trimmed,
                        isSynced = parsed.isSynced,
                        sourceName = label
                    )
                )
            }
        }

    suspend fun deleteLyricsForTrack(trackId: Long) = withContext(Dispatchers.IO) {
        dao.deleteLyricsForTrack(trackId)
    }

    suspend fun saveRecentSearch(query: String) = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.length >= 2) {
            dao.insertRecentSearch(RecentSearchEntity(query = clean))
        }
    }

    suspend fun removeRecentSearch(query: String) = withContext(Dispatchers.IO) {
        dao.deleteRecentSearch(query)
    }

    suspend fun clearRecentSearches() = withContext(Dispatchers.IO) {
        dao.clearRecentSearches()
    }

    suspend fun clearPlayHistory() = withContext(Dispatchers.IO) {
        dao.clearPlayHistory()
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
    }

    private fun CachedTrackEntity.toDomainTrack(
        isFavourite: Boolean,
        playCount: Int,
        lastPlayedAt: Long?
    ): Track = Track(
        id = id,
        title = title,
        artist = artist,
        album = album,
        albumId = albumId,
        durationMs = durationMs,
        contentUri = contentUri,
        albumArtUri = albumArtUri,
        dateAddedSec = dateAddedSec,
        trackNumber = trackNumber,
        year = year,
        sizeBytes = sizeBytes,
        folderPath = folderPath,
        isFavourite = isFavourite,
        playCount = playCount,
        lastPlayedAt = lastPlayedAt,
        isAvailable = isAvailable,
        isDemoTrack = isDemoTrack
    )
}
