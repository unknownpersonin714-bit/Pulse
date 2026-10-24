package com.example.util

import com.example.domain.model.LyricsLine
import com.example.domain.model.ParsedLyrics

object LrcParser {
    // Matches [mm:ss.xx], [mm:ss.xxx], or [mm:ss]
    private val TIMESTAMP_REGEX = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    // Matches metadata tags like [ar:Artist], [ti:Title], [al:Album], [by:Creator], [offset:0]
    private val METADATA_TAG_REGEX = Regex("""^\[(ar|ti|al|au|by|offset|length|re|ve):.*]$""", RegexOption.IGNORE_CASE)

    fun parse(trackId: Long, rawContent: String, sourceLabel: String = "Local LRC"): ParsedLyrics {
        val normalized = rawContent.replace("\r\n", "\n").replace("\r", "\n").trim()
        if (normalized.isBlank()) {
            return ParsedLyrics(
                trackId = trackId,
                isSynced = false,
                lines = emptyList(),
                rawText = "",
                sourceLabel = sourceLabel
            )
        }

        val syncedEntries = mutableListOf<LyricsLine>()
        val plainEntries = mutableListOf<LyricsLine>()

        for (rawLine in normalized.lines()) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) continue
            if (METADATA_TAG_REGEX.matches(trimmed)) continue

            val matches = TIMESTAMP_REGEX.findAll(trimmed).toList()
            if (matches.isNotEmpty()) {
                val lyricText = trimmed.replace(TIMESTAMP_REGEX, "").trim()
                for (match in matches) {
                    val minutes = match.groupValues[1].toLongOrNull() ?: 0L
                    val seconds = match.groupValues[2].toLongOrNull() ?: 0L
                    val fractionStr = match.groupValues.getOrNull(3).orEmpty()
                    val millis = when (fractionStr.length) {
                        0 -> 0L
                        1 -> (fractionStr.toLongOrNull() ?: 0L) * 100L
                        2 -> (fractionStr.toLongOrNull() ?: 0L) * 10L
                        else -> fractionStr.take(3).toLongOrNull() ?: 0L
                    }
                    val totalMs = (minutes * 60_000L) + (seconds * 1_000L) + millis
                    syncedEntries.add(
                        LyricsLine(
                            timestampMs = totalMs,
                            text = lyricText.ifEmpty { "•••" }
                        )
                    )
                }
            } else {
                plainEntries.add(LyricsLine(timestampMs = null, text = trimmed))
            }
        }

        return if (syncedEntries.isNotEmpty()) {
            val sorted = syncedEntries.sortedBy { it.timestampMs ?: 0L }
            ParsedLyrics(
                trackId = trackId,
                isSynced = true,
                lines = sorted,
                rawText = normalized,
                sourceLabel = sourceLabel
            )
        } else {
            ParsedLyrics(
                trackId = trackId,
                isSynced = false,
                lines = plainEntries,
                rawText = normalized,
                sourceLabel = sourceLabel
            )
        }
    }

    /**
     * Returns the index of the active lyric line for a given playback position in milliseconds.
     */
    fun findActiveLineIndex(lines: List<LyricsLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var activeIndex = -1
        for (i in lines.indices) {
            val ts = lines[i].timestampMs ?: continue
            if (ts <= positionMs + 150L) {
                activeIndex = i
            } else {
                break
            }
        }
        return activeIndex
    }
}
