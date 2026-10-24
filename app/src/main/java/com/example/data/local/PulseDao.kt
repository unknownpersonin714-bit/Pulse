package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PulseDao {
    // --- Cached Tracks ---
    @Query("SELECT * FROM cached_tracks ORDER BY title COLLATE NOCASE ASC")
    fun observeAllCachedTracks(): Flow<List<CachedTrackEntity>>

    @Query("SELECT * FROM cached_tracks")
    suspend fun getAllCachedTracksOnce(): List<CachedTrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCachedTracks(tracks: List<CachedTrackEntity>)

    @Query("DELETE FROM cached_tracks WHERE isDemoTrack = 0 AND id NOT IN (:validIds)")
    suspend fun removeMissingMediaStoreTracks(validIds: List<Long>)

    @Query("DELETE FROM cached_tracks WHERE isDemoTrack = 0")
    suspend fun clearAllMediaStoreTracks()

    @Query("DELETE FROM cached_tracks WHERE isDemoTrack = 1")
    suspend fun clearDemoTracks()

    @Query("UPDATE cached_tracks SET isAvailable = :available WHERE id = :trackId")
    suspend fun setTrackAvailability(trackId: Long, available: Boolean)

    // --- Favourites ---
    @Query("SELECT * FROM favourites ORDER BY addedAt DESC")
    fun observeFavourites(): Flow<List<FavouriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favourites WHERE trackId = :trackId)")
    suspend fun isTrackFavourite(trackId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavourite(favourite: FavouriteEntity)

    @Query("DELETE FROM favourites WHERE trackId = :trackId")
    suspend fun removeFavourite(trackId: Long)

    @Transaction
    suspend fun toggleFavourite(trackId: Long): Boolean {
        return if (isTrackFavourite(trackId)) {
            removeFavourite(trackId)
            false
        } else {
            addFavourite(FavouriteEntity(trackId = trackId))
            true
        }
    }

    // --- Playlists ---
    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlist_tracks ORDER BY playlistId ASC, position ASC")
    fun observeAllPlaylistCrossRefs(): Flow<List<PlaylistTrackCrossRef>>

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun getPlaylistTracksOnce(playlistId: Long): List<PlaylistTrackCrossRef>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :newName, updatedAt = :updatedAt WHERE id = :playlistId")
    suspend fun renamePlaylist(playlistId: Long, newName: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylistById(playlistId: Long)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun deleteAllTracksFromPlaylist(playlistId: Long)

    @Transaction
    suspend fun deletePlaylistWithTracks(playlistId: Long) {
        deleteAllTracksFromPlaylist(playlistId)
        deletePlaylistById(playlistId)
    }

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun getMaxPositionInPlaylist(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTrackCrossRef(ref: PlaylistTrackCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTrackCrossRefs(refs: List<PlaylistTrackCrossRef>)

    @Query("UPDATE playlists SET updatedAt = :updatedAt WHERE id = :playlistId")
    suspend fun touchPlaylist(playlistId: Long, updatedAt: Long = System.currentTimeMillis())

    @Transaction
    suspend fun addTrackToPlaylist(playlistId: Long, trackId: Long) {
        val existing = getPlaylistTracksOnce(playlistId)
        if (existing.any { it.trackId == trackId }) return
        val nextPos = (existing.maxOfOrNull { it.position } ?: -1) + 1
        insertPlaylistTrackCrossRef(
            PlaylistTrackCrossRef(
                playlistId = playlistId,
                trackId = trackId,
                position = nextPos
            )
        )
        touchPlaylist(playlistId)
    }

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun deleteTrackFromPlaylistRaw(playlistId: Long, trackId: Long)

    @Transaction
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
        deleteTrackFromPlaylistRaw(playlistId, trackId)
        val remaining = getPlaylistTracksOnce(playlistId).sortedBy { it.position }
        val normalized = remaining.mapIndexed { index, ref -> ref.copy(position = index) }
        insertPlaylistTrackCrossRefs(normalized)
        touchPlaylist(playlistId)
    }

    @Transaction
    suspend fun reorderPlaylistTracks(playlistId: Long, orderedTrackIds: List<Long>) {
        val currentRefs = getPlaylistTracksOnce(playlistId).associateBy { it.trackId }
        deleteAllTracksFromPlaylist(playlistId)
        val updated = orderedTrackIds.mapIndexed { index, trackId ->
            val old = currentRefs[trackId]
            PlaylistTrackCrossRef(
                playlistId = playlistId,
                trackId = trackId,
                position = index,
                addedAt = old?.addedAt ?: System.currentTimeMillis()
            )
        }
        insertPlaylistTrackCrossRefs(updated)
        touchPlaylist(playlistId)
    }

    // --- Play History & Stats ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlayHistory(entry: PlayHistoryEntity)

    @Query("SELECT * FROM play_history ORDER BY playedAt DESC LIMIT 200")
    fun observeRecentPlayHistory(): Flow<List<PlayHistoryEntity>>

    @Query(
        """
        SELECT trackId,
               COUNT(*) as playCount,
               MAX(playedAt) as lastPlayedAt,
               SUM(listenedDurationMs) as totalDurationMs
        FROM play_history
        GROUP BY trackId
        """
    )
    fun observeTrackPlayStats(): Flow<List<TrackPlayStatsTuple>>

    @Query("DELETE FROM play_history")
    suspend fun clearPlayHistory()

    // --- Lyrics ---
    @Query("SELECT * FROM lyrics_store WHERE trackId = :trackId LIMIT 1")
    fun observeLyricsForTrack(trackId: Long): Flow<LyricsEntity?>

    @Query("SELECT * FROM lyrics_store WHERE trackId = :trackId LIMIT 1")
    suspend fun getLyricsForTrackOnce(trackId: Long): LyricsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveLyrics(entity: LyricsEntity)

    @Query("DELETE FROM lyrics_store WHERE trackId = :trackId")
    suspend fun deleteLyricsForTrack(trackId: Long)

    // --- Recent Searches ---
    @Query("SELECT * FROM recent_searches ORDER BY searchedAt DESC LIMIT 12")
    fun observeRecentSearches(): Flow<List<RecentSearchEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentSearch(search: RecentSearchEntity)

    @Query("DELETE FROM recent_searches WHERE query = :query")
    suspend fun deleteRecentSearch(query: String)

    @Query("DELETE FROM recent_searches")
    suspend fun clearRecentSearches()
}
