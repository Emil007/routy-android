package com.routy.app.logic.route

import com.routy.app.logic.api.RouteStation
import com.routy.app.logic.geo.LatLng
import com.routy.app.logic.geo.haversineMeters

/**
 * Tracks sequential waypoint completion while walking an active route with Track on.
 * Uses the same adaptive radius as voice announcements (K2).
 */
class WaypointProgressTracker(private val stations: List<RouteStation>) {
    private var nextIndex = 0

    val completedIndex: Int get() = if (nextIndex == 0) -1 else nextIndex - 1
    val nextIndexOrNull: Int? get() = if (nextIndex < stations.size) nextIndex else null
    val isFinalCompleted: Boolean get() = stations.isNotEmpty() && nextIndex >= stations.size
    val totalCount: Int get() = stations.size
    val completedCount: Int get() = nextIndex.coerceAtMost(stations.size)

    private fun radiusForNextStation(): Double {
        if (nextIndex >= stations.size) return voiceAnnounceRadiusM(stations.lastIndex, stations)
        return voiceAnnounceRadiusM(nextIndex, stations)
    }

    fun onLocationUpdate(location: LatLng): Int? {
        if (nextIndex >= stations.size) return null
        val station = stations[nextIndex]
        val distance = haversineMeters(location, LatLng(station.lat, station.lng))
        if (distance > radiusForNextStation()) return null
        val completed = nextIndex
        nextIndex++
        return completed
    }

    fun restore(completedUpTo: Int) {
        nextIndex = (completedUpTo + 1).coerceIn(0, stations.size)
    }

    fun reset() {
        nextIndex = 0
    }
}

/** True when all stations are completed and the walker is near the final node (K2). */
fun shouldAutoCompleteRoute(
    progressTracker: WaypointProgressTracker,
    stations: List<RouteStation>,
    location: LatLng,
): Boolean {
    if (!progressTracker.isFinalCompleted || stations.isEmpty()) return false
    val lastIdx = stations.lastIndex
    val radiusM = voiceAnnounceRadiusM(lastIdx, stations)
    val last = stations.last()
    return haversineMeters(location, LatLng(last.lat, last.lng)) <= radiusM
}
