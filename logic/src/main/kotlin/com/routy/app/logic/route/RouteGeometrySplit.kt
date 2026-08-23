package com.routy.app.logic.route

import com.routy.app.logic.api.GeoPoint
import com.routy.app.logic.api.RouteStation
import com.routy.app.logic.geo.LatLng
import com.routy.app.logic.geo.closestPointOnPath

/**
 * Planned route polyline from the next uncompleted station onward (K1).
 * Completed legs are shown via [trackedGeometry] only.
 */
fun remainingRouteGeometry(
    routeGeometry: List<GeoPoint>,
    stations: List<RouteStation>,
    completedWaypointIndex: Int,
): List<GeoPoint> {
    if (routeGeometry.size < 2 || stations.isEmpty()) return routeGeometry
    if (completedWaypointIndex < 0) return routeGeometry
    if (completedWaypointIndex >= stations.lastIndex) return emptyList()

    val path = routeGeometry.map { LatLng(it.lat, it.lng) }
    val nextStation = stations[completedWaypointIndex + 1]
    val closest = closestPointOnPath(path, LatLng(nextStation.lat, nextStation.lng)) ?: return routeGeometry

    val result = mutableListOf(GeoPoint(closest.point.lat, closest.point.lng))
    for (i in (closest.index + 1) until routeGeometry.size) {
        result.add(routeGeometry[i])
    }
    return if (result.size >= 2) result else routeGeometry
}
