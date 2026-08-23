package com.routy.app.logic.route

import com.routy.app.logic.geo.LatLng
import com.routy.app.logic.geo.closestPointOnPath

private const val OFF_PATH_DISTANCE_M = 40.0
private const val CONSECUTIVE_SAMPLES = 3

/**
 * Fires at most once per walk when the walker stays farther than ~40 m from the planned polyline
 * for several consecutive location updates while Track is on.
 */
class OffPathDetector(private val routePolyline: List<LatLng>) {
    private var consecutiveOffPath = 0
    private var warned = false

    /** Returns true the first time off-path threshold is crossed (caller should haptic + TTS once). */
    fun onLocation(location: LatLng): Boolean {
        if (warned || routePolyline.size < 2) return false
        val distanceM = closestPointOnPath(routePolyline, location)?.distanceM ?: return false
        if (distanceM > OFF_PATH_DISTANCE_M) {
            consecutiveOffPath++
            if (consecutiveOffPath >= CONSECUTIVE_SAMPLES) {
                warned = true
                return true
            }
        } else {
            consecutiveOffPath = 0
        }
        return false
    }

    fun reset() {
        consecutiveOffPath = 0
        warned = false
    }
}
