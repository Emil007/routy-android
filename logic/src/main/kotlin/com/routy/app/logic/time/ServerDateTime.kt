package com.routy.app.logic.time

import java.time.Instant
import java.util.Locale

/** Parse server timestamps that may be SQLite space-separated or already ISO-8601 with a zone. */
fun parseServerInstant(iso: String): Instant {
    val normalized = iso.trim().replace(' ', 'T')
    val hasZone = normalized.endsWith('Z', ignoreCase = true) ||
        normalized.contains('+') ||
        Regex("T\\d{2}:\\d{2}:\\d{2}-").containsMatchIn(normalized)
    return Instant.parse(if (hasZone) normalized else "${normalized}Z")
}

/** Whole hours with one decimal — avoids truncating 90 min to 1 h. */
fun formatDurationHours(totalMinutes: Int): String {
    if (totalMinutes % 60 == 0) return (totalMinutes / 60).toString()
    return "%.1f".format(Locale.US, totalMinutes / 60.0)
}
