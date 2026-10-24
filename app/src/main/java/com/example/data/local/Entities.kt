package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "cached_tracks")
data class CachedTrackEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val contentUri: String,
    val albumArtUri: String?,
    val dateAddedSec: Long,
    val trackNumber: Int,
    val year: Int,
    val sizeBytes: Long,
    val folderPath: String,
    val isAvailable: Boolean = true,
    val isDemoTrack: Boolean = false
)

@Entity(tableName = "favourites")
data class FavouriteEntity(
    @PrimaryKey val trackId: Long,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId"],
    indices = [Index(value = ["playlistId"]), Index(value = ["trackId"])]
)
data class PlaylistTrackCrossRef(
    val playlistId: Long,
    val trackId: Long,
    val position: Int,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "play_history",
    indices = [Index(value = ["trackId"]), Index(value = ["playedAt"])]
)
data class PlayHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val trackId: Long,
    val playedAt: Long = System.currentTimeMillis(),
    val listenedDurationMs: Long = 0L
)

@Entity(tableName = "lyrics_store")
data class LyricsEntity(
    @PrimaryKey val trackId: Long,
    val rawContent: String,
    val isSynced: Boolean,
    val sourceName: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "recent_searches")
data class RecentSearchEntity(
    @PrimaryKey val query: String,
    val searchedAt: Long = System.currentTimeMillis()
)

data class TrackPlayStatsTuple(
    val trackId: Long,
    val playCount: Int,
    val lastPlayedAt: Long?,
    val totalDurationMs: Long
)
