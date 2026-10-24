package com.example

import com.example.domain.model.Track
import com.example.domain.usecase.SmartRecommendationsUseCase
import com.example.util.LrcParser
import com.example.util.TimeFormatUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `LrcParser parses timestamped lyrics and finds active line accurately`() {
        val sampleLrc = """
            [ti:Obsidian Resonance]
            [ar:PULSE Lab]
            [00:00.00]First line of the song
            [00:04.50]Second line at four point five seconds
            [00:10.250]Third line with millisecond precision
        """.trimIndent()

        val parsed = LrcParser.parse(trackId = 101L, rawContent = sampleLrc)
        assertTrue(parsed.isSynced)
        assertEquals(3, parsed.lines.size)
        assertEquals(0L, parsed.lines[0].timestampMs)
        assertEquals(4500L, parsed.lines[1].timestampMs)
        assertEquals(10250L, parsed.lines[2].timestampMs)

        assertEquals(0, LrcParser.findActiveLineIndex(parsed.lines, 2000L))
        assertEquals(1, LrcParser.findActiveLineIndex(parsed.lines, 4600L))
        assertEquals(2, LrcParser.findActiveLineIndex(parsed.lines, 15000L))
    }

    @Test
    fun `LrcParser distinguishes plain text lyrics from synchronized LRC`() {
        val plain = """
            Verse 1: Walking through the quiet night
            Chorus: Pure monochrome sound
        """.trimIndent()

        val parsed = LrcParser.parse(trackId = 102L, rawContent = plain)
        assertFalse(parsed.isSynced)
        assertEquals(2, parsed.lines.size)
    }

    @Test
    fun `TimeFormatUtil formats durations cleanly`() {
        assertEquals("0:05", TimeFormatUtil.formatDuration(5_000L))
        assertEquals("3:45", TimeFormatUtil.formatDuration(225_000L))
        assertEquals("1:02:03", TimeFormatUtil.formatDuration(3_723_000L))
    }

    @Test
    fun `SmartRecommendationsUseCase produces deterministic rankings from local metadata`() {
        val tracks = listOf(
            Track(
                id = 1L,
                title = "Alpha",
                artist = "Artist A",
                album = "Album 1",
                albumId = 10L,
                durationMs = 180_000L,
                contentUri = "content://media/external/audio/media/1",
                albumArtUri = null,
                dateAddedSec = 1000L,
                isFavourite = true,
                playCount = 12,
                lastPlayedAt = 5000L
            ),
            Track(
                id = 2L,
                title = "Beta",
                artist = "Artist B",
                album = "Album 2",
                albumId = 20L,
                durationMs = 200_000L,
                contentUri = "content://media/external/audio/media/2",
                albumArtUri = null,
                dateAddedSec = 2000L,
                isFavourite = false,
                playCount = 3,
                lastPlayedAt = 9000L
            ),
            Track(
                id = 3L,
                title = "Gamma",
                artist = "Artist A",
                album = "Album 1",
                albumId = 10L,
                durationMs = 210_000L,
                contentUri = "content://media/external/audio/media/3",
                albumArtUri = null,
                dateAddedSec = 3000L,
                isFavourite = false,
                playCount = 0,
                lastPlayedAt = null
            )
        )

        val recs1 = SmartRecommendationsUseCase.compute(tracks, daySeed = 2026297L)
        val recs2 = SmartRecommendationsUseCase.compute(tracks, daySeed = 2026297L)

        assertEquals(2L, recs1.recentlyPlayed.first().id)
        assertEquals(1L, recs1.mostPlayed.first().id)
        assertEquals(3L, recs1.recentlyAdded.first().id)
        assertEquals(recs1.dailyMix.map { it.id }, recs2.dailyMix.map { it.id })
    }
}
