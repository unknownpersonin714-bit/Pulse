package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.CachedTrackEntity
import com.example.data.local.PlaylistEntity
import com.example.data.local.PulseDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var database: PulseDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, PulseDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `app_name string resource is PULSE`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("PULSE", appName)
    }

    @Test
    fun `Room DAO persists playlists, track reordering, and favourites without deleting cached tracks`() = runBlocking {
        val dao = database.pulseDao()

        val tracks = listOf(
            CachedTrackEntity(
                id = 10L,
                title = "Track One",
                artist = "Artist 1",
                album = "Album 1",
                albumId = 1L,
                durationMs = 180_000L,
                contentUri = "content://media/external/audio/media/10",
                albumArtUri = null,
                dateAddedSec = 100L,
                trackNumber = 1,
                year = 2025,
                sizeBytes = 4096L,
                folderPath = "/Music"
            ),
            CachedTrackEntity(
                id = 20L,
                title = "Track Two",
                artist = "Artist 2",
                album = "Album 2",
                albumId = 2L,
                durationMs = 200_000L,
                contentUri = "content://media/external/audio/media/20",
                albumArtUri = null,
                dateAddedSec = 200L,
                trackNumber = 2,
                year = 2025,
                sizeBytes = 8192L,
                folderPath = "/Music"
            )
        )
        dao.upsertCachedTracks(tracks)

        // Toggle favourite
        val favNow = dao.toggleFavourite(10L)
        assertTrue(favNow)
        assertTrue(dao.isTrackFavourite(10L))
        val unfavNow = dao.toggleFavourite(10L)
        assertFalse(unfavNow)

        // Create playlist, add tracks, reorder, rename, delete
        val playlistId = dao.insertPlaylist(PlaylistEntity(name = "Night Drive"))
        dao.addTrackToPlaylist(playlistId, 10L)
        dao.addTrackToPlaylist(playlistId, 20L)

        val initialOrder = dao.getPlaylistTracksOnce(playlistId).map { it.trackId }
        assertEquals(listOf(10L, 20L), initialOrder)

        dao.reorderPlaylistTracks(playlistId, listOf(20L, 10L))
        val reordered = dao.getPlaylistTracksOnce(playlistId).map { it.trackId }
        assertEquals(listOf(20L, 10L), reordered)

        dao.renamePlaylist(playlistId, "Late Night Synth")
        val playlists = dao.observePlaylists().first()
        assertEquals("Late Night Synth", playlists.first().name)

        // Delete playlist and verify cached tracks still exist
        dao.deletePlaylistWithTracks(playlistId)
        assertTrue(dao.observePlaylists().first().isEmpty())
        assertEquals(2, dao.getAllCachedTracksOnce().size)
    }
}
