package com.routy.app.logic.route

import com.routy.app.logic.api.RouteStation
import com.routy.app.logic.geo.LatLng
import com.routy.app.logic.geo.haversineMeters

private const val WAYPOINT_RADIUS_M = 50.0

/**
 * Tracks sequential waypoint completion while walking an active route. Mirrors [VoiceCueTracker]'s
 * radius logic but exposes progress state for UI overlays and route-proof gating.
 */
class WaypointProgressTracker(private val stations: List<RouteStation>) {
    private var nextIndex = 0

    val completedIndex: Int get() = if (nextIndex == 0) -1 else nextIndex - 1
    val nextIndexOrNull: Int? get() = if (nextIndex < stations.size) nextIndex else null
    val isFinalCompleted: Boolean get() = stations.isNotEmpty() && nextIndex >= stations.size
    val totalCount: Int get() = stations.size
    val completedCount: Int get() = nextIndex.coerceAtMost(stations.size)

    fun onLocationUpdate(location: LatLng): Int? {
        if (nextIndex >= stations.size) return null
        val station = stations[nextIndex]
        val distance = haversineMeters(location, LatLng(station.lat, station.lng))
        if (distance > WAYPOINT_RADIUS_M) return null
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
