package com.routy.app.logic.route

import com.routy.app.logic.geo.LatLng
import com.routy.app.logic.geo.haversineMeters

private const val MAX_ANNOUNCE_RADIUS_M = 22.0
private const val MIN_ANNOUNCE_RADIUS_M = 9.0
private const val DISTANCE_FRACTION = 0.28

/** Adaptive announce/waypoint radius — tighter while Track is on (react closer to the node). */
fun voiceAnnounceRadiusM(stationIndex: Int, stations: List<com.routy.app.logic.api.RouteStation>): Double {
    val next = stations.getOrNull(stationIndex + 1)
    if (next == null) return MAX_ANNOUNCE_RADIUS_M
    val here = stations[stationIndex]
    val legM = haversineMeters(LatLng(here.lat, here.lng), LatLng(next.lat, next.lng))
    return legM
        .let { MAX_ANNOUNCE_RADIUS_M.coerceAtMost(DISTANCE_FRACTION * it) }
        .coerceAtLeast(MIN_ANNOUNCE_RADIUS_M)
}
