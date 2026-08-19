package com.routy.app.logic.time

import java.time.Instant

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
    val hours = totalMinutes / 60.0
    return if (hours % 1.0 == 0.0) hours.toInt().toString() else "%.1f".format(hours)
}
