package com.example.domain.model

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val contentUri: String,
    val albumArtUri: String?,
    val dateAddedSec: Long,
    val trackNumber: Int = 0,
    val year: Int = 0,
    val sizeBytes: Long = 0L,
    val folderPath: String = "",
    val isFavourite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayedAt: Long? = null,
    val isAvailable: Boolean = true,
    val isDemoTrack: Boolean = false
)

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val artworkUri: String?,
    val trackCount: Int,
    val totalDurationMs: Long,
    val year: Int,
    val tracks: List<Track>
)

data class Artist(
    val name: String,
    val trackCount: Int,
    val albumCount: Int,
    val totalDurationMs: Long,
    val artworkUri: String?,
    val tracks: List<Track>
)

data class PlaylistSummary(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val tracks: List<Track>
) {
    val trackCount: Int get() = tracks.size
    val totalDurationMs: Long get() = tracks.sumOf { it.durationMs }
    val coverArtworkUri: String? get() = tracks.firstOrNull { !it.albumArtUri.isNullOrBlank() }?.albumArtUri
}

data class LyricsLine(
    val timestampMs: Long?,
    val text: String
)

data class ParsedLyrics(
    val trackId: Long,
    val isSynced: Boolean,
    val lines: List<LyricsLine>,
    val rawText: String,
    val sourceLabel: String
)

enum class RepeatModeOption {
    OFF,
    ALL,
    ONE
}

enum class SortOption(val label: String) {
    TITLE("Title (A–Z)"),
    ARTIST("Artist"),
    ALBUM("Album"),
    DURATION("Duration"),
    DATE_ADDED("Recently Added")
}

enum class ThemeOption(val label: String) {
    DARK("Pure Black (OLED)"),
    LIGHT("Monochrome Light"),
    SYSTEM("System Default")
}

data class ListeningStats(
    val totalPlays: Int = 0,
    val totalListeningTimeMs: Long = 0L,
    val uniqueTracksPlayed: Int = 0,
    val mostPlayedTrack: Track? = null,
    val topArtist: String? = null
)

data class ScanProgress(
    val isScanning: Boolean = false,
    val scannedCount: Int = 0,
    val totalCount: Int = 0,
    val errorMessage: String? = null,
    val lastScanTimestamp: Long = 0L
)
