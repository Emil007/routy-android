package com.routy.app.logic.recording

import com.routy.app.logic.geo.estimateMinutes

/**
 * M17: Live recording duration from the GPS timestamps (first point → last point), not the
 * speed-based estimate. Pauses have already been merged into the point list by the service,
 * so this is elapsed *active* time; the speed estimate stays as fallback when no timestamps
 * exist. Never below 1 minute.
 */
fun recordingDurationMin(points: List<RecordingPoint>, lengthM: Double, walkSpeedKmh: Double): Int {
    val first = points.firstOrNull()?.timestampMs ?: 0L
    val last = points.lastOrNull()?.timestampMs ?: 0L
    if (last > first) {
        val minutes = ((last - first) / 60_000.0).let { if (it < 1) 1 else it.toInt() }
        return minutes
    }
    return estimateMinutes(lengthM, walkSpeedKmh)
}
