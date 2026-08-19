package com.routy.app.logic.recording

import com.routy.app.logic.geo.LatLng
import com.routy.app.logic.geo.haversineMeters

/** Adaptive GPS poll interval while recording — slower when stationary to save battery. */
fun recordingLocationIntervalMs(recentPoints: List<RecordingPoint>): Long {
    if (recentPoints.size < 2) return FAST_INTERVAL_MS
    val last = recentPoints.last()
    val prev = recentPoints[recentPoints.lastIndex - 1]
    val dtMs = (last.timestampMs - prev.timestampMs).coerceAtLeast(1L)
    val speedMps = haversineMeters(LatLng(prev.lat, prev.lng), LatLng(last.lat, last.lng)) / (dtMs / 1000.0)
    return when {
        speedMps >= 0.5 -> FAST_INTERVAL_MS
        speedMps >= 0.15 -> MEDIUM_INTERVAL_MS
        else -> SLOW_INTERVAL_MS
    }
}

private const val FAST_INTERVAL_MS = 3_000L
private const val MEDIUM_INTERVAL_MS = 6_000L
private const val SLOW_INTERVAL_MS = 12_000L
