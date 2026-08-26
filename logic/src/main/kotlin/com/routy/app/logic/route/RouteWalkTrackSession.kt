package com.routy.app.logic.route

import com.routy.app.logic.geo.LatLng
import com.routy.app.logic.geo.haversineMeters
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private const val MIN_POINT_DISTANCE_M = 3.0
/** Ignore accuracy worse than this for on-map traced geometry — all points kept for GPX. */
const val ROUTE_TRACK_GEOMETRY_ACCURACY_MAX_M = 30.0

/** Cue / progress / off-path require a reported accuracy at least this good (null ≠ usable). */
const val ROUTE_CUE_ACCURACY_MAX_M = 15.0

/** True when a fix is accurate enough for off-path / waypoint / voice cues. Missing accuracy is not usable. */
fun isRouteTrackAccuracyUsable(accuracy: Float?): Boolean =
    accuracy != null && accuracy <= ROUTE_CUE_ACCURACY_MAX_M

class RouteWalkTrackSession {
    private val _points = mutableListOf<RouteWalkTrackPoint>()
    val points: List<RouteWalkTrackPoint> get() = _points

    fun restore(saved: List<RouteWalkTrackPoint>) {
        _points.clear()
        _points.addAll(saved)
    }

    fun addFix(
        lat: Double,
        lng: Double,
        ele: Double?,
        timestampMs: Long,
        accuracy: Float?,
        speed: Float?,
        bearing: Float?,
    ) {
        val last = _points.lastOrNull()
        if (last != null) {
            val moved = haversineMeters(LatLng(last.lat, last.lng), LatLng(lat, lng))
            if (moved < MIN_POINT_DISTANCE_M) return
        }
        _points.add(
            RouteWalkTrackPoint(
                lat = lat,
                lng = lng,
                ele = ele,
                time = Instant.ofEpochMilli(timestampMs).atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT),
                accuracy = accuracy?.toDouble()?.takeIf { it >= 0 },
                speed = speed?.toDouble()?.takeIf { it >= 0 },
                bearing = bearing?.toDouble()?.takeIf { it >= 0 },
            ),
        )
    }

    /** Points accurate enough for on-map traced geometry (completed hops overlay). */
    fun geometryPoints(): List<LatLng> = _points
        .filter { point -> point.accuracy == null || point.accuracy <= ROUTE_TRACK_GEOMETRY_ACCURACY_MAX_M }
        .map { LatLng(it.lat, it.lng) }

    fun clear() {
        _points.clear()
    }
}
