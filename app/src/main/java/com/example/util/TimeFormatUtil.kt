package com.example.util

import java.util.Locale

object TimeFormatUtil {
    fun formatDuration(durationMs: Long): String {
        val safeMs = durationMs.coerceAtLeast(0L)
        val totalSeconds = safeMs / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    fun formatTotalDurationCompact(durationMs: Long): String {
        val safeMs = durationMs.coerceAtLeast(0L)
        val totalMinutes = safeMs / 60_000L
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            totalMinutes > 0 -> "${totalMinutes} min"
            else -> "${(safeMs / 1000L)} sec"
        }
    }
}
