package com.example.domain.usecase

import com.example.domain.model.Track
import java.util.Calendar
import kotlin.random.Random

data class SmartRecommendations(
    val recentlyPlayed: List<Track> = emptyList(),
    val mostPlayed: List<Track> = emptyList(),
    val recentlyAdded: List<Track> = emptyList(),
    val dailyMix: List<Track> = emptyList(),
    val rediscover: List<Track> = emptyList(),
    val historyNotice: String? = null
)

object SmartRecommendationsUseCase {

    fun compute(allTracks: List<Track>, daySeed: Long = currentDaySeed()): SmartRecommendations {
        val available = allTracks.filter { it.isAvailable }
        if (available.isEmpty()) {
            return SmartRecommendations()
        }

        val recentlyPlayed = available
            .filter { it.lastPlayedAt != null }
            .sortedByDescending { it.lastPlayedAt ?: 0L }
            .take(12)

        val mostPlayed = available
            .filter { it.playCount > 0 }
            .sortedWith(
                compareByDescending<Track> { it.playCount }
                    .thenByDescending { it.lastPlayedAt ?: 0L }
            )
            .take(12)

        val recentlyAdded = available
            .sortedWith(
                compareByDescending<Track> { it.dateAddedSec }
                    .thenBy { it.title.lowercase() }
            )
            .take(12)

        // Deterministic Daily Mix combining favourites, top artists, and daily seeded variety
        val topArtists = available
            .filter { it.playCount > 0 || it.isFavourite }
            .groupBy { it.artist }
            .entries
            .sortedByDescending { (_, list) -> list.sumOf { it.playCount * 2 + if (it.isFavourite) 3 else 0 } }
            .take(4)
            .map { it.key }
            .toSet()

        val seededRandom = Random(daySeed)
        val shuffledPool = available.shuffled(seededRandom)

        val dailyMix = shuffledPool
            .sortedByDescending { track ->
                var score = 0
                if (track.isFavourite) score += 5
                if (track.artist in topArtists) score += 3
                if (track.playCount > 0) score += 2
                score
            }
            .take(15)

        // Rediscover: tracks with 0 or low plays rotated deterministically by day seed
        val rediscover = shuffledPool
            .sortedWith(
                compareBy<Track> { it.playCount }
                    .thenBy { it.lastPlayedAt ?: 0L }
            )
            .take(12)

        val notice = when {
            available.size < 3 ->
                "Your library currently has ${available.size} track(s). Add more local audio files to unlock richer Daily Mixes and discovery rotations."
            recentlyPlayed.isEmpty() ->
                "Start playing tracks to build your local listening history and personalized Most Played rankings."
            else -> null
        }

        return SmartRecommendations(
            recentlyPlayed = recentlyPlayed,
            mostPlayed = mostPlayed,
            recentlyAdded = recentlyAdded,
            dailyMix = dailyMix,
            rediscover = rediscover,
            historyNotice = notice
        )
    }

    private fun currentDaySeed(): Long {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR).toLong()
        val dayOfYear = cal.get(Calendar.DAY_OF_YEAR).toLong()
        return year * 1000L + dayOfYear
    }
}
