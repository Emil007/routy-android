package com.routy.app.logic.geo

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Kotlin port of src/lib/geo.ts — kept numerically identical so voice-cue timing matches the web app. */
data class LatLng(val lat: Double, val lng: Double)

private const val EARTH_RADIUS_M = 6371000.0

fun haversineMeters(a: LatLng, b: LatLng): Double {
    val phi1 = a.lat * PI / 180
    val phi2 = b.lat * PI / 180
    val dPhi = (b.lat - a.lat) * PI / 180
    val dLambda = (b.lng - a.lng) * PI / 180
    val sinPhi = sin(dPhi / 2)
    val sinLambda = sin(dLambda / 2)
    val h = sinPhi * sinPhi + cos(phi1) * cos(phi2) * sinLambda * sinLambda
    return EARTH_RADIUS_M * 2 * atan2(sqrt(h), sqrt(max(0.0, 1 - h)))
}

/** Initial bearing from a to b, in degrees, 0-360 where 0 is true north. */
fun bearing(a: LatLng, b: LatLng): Double {
    val lat1 = a.lat * PI / 180
    val lat2 = b.lat * PI / 180
    val dLng = (b.lng - a.lng) * PI / 180
    val y = sin(dLng) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
    val theta = atan2(y, x)
    return (theta * 180 / PI + 360) % 360
}

enum class CompassPoint { N, NE, E, SE, S, SW, W, NW }

private val COMPASS_POINTS = listOf(
    CompassPoint.N,
    CompassPoint.NE,
    CompassPoint.E,
    CompassPoint.SE,
    CompassPoint.S,
    CompassPoint.SW,
    CompassPoint.W,
    CompassPoint.NW,
)

/** Buckets a 0-360deg bearing into one of 8 compass points. */
fun compassDirection(bearingDeg: Double): CompassPoint {
    val normalized = (bearingDeg % 360 + 360) % 360
    return COMPASS_POINTS[(normalized / 45).roundToInt() % 8]
}

fun pathLengthMeters(points: List<LatLng>): Double {
    var total = 0.0
    for (i in 1 until points.size) {
        total += haversineMeters(points[i - 1], points[i])
    }
    return total
}

fun estimateMinutes(lengthM: Double, walkSpeedKmh: Double): Int {
    val speed = if (walkSpeedKmh > 0) walkSpeedKmh else 5.0
    return (lengthM / 1000 / speed * 60).roundToInt()
}

data class ElevationStats(val gainM: Int, val lossM: Int, val minM: Int, val maxM: Int)

/** Mirrors src/lib/geo.ts's elevationStats: needs at least 2 elevation samples, else null. */
fun elevationStats(elevationsM: List<Double>): ElevationStats? {
    if (elevationsM.size < 2) return null
    var gain = 0.0
    var loss = 0.0
    for (i in 1 until elevationsM.size) {
        val diff = elevationsM[i] - elevationsM[i - 1]
        if (diff > 0) gain += diff else loss += -diff
    }
    return ElevationStats(
        gainM = gain.roundToInt(),
        lossM = loss.roundToInt(),
        minM = elevationsM.min().roundToInt(),
        maxM = elevationsM.max().roundToInt(),
    )
}

fun reversePoints(points: List<LatLng>): List<LatLng> = points.reversed()

data class ClosestPointResult(val point: LatLng, val index: Int, val distanceM: Double)

/** Port of src/lib/geo.ts closestPointOnPath — for segment tap / split placement. */
fun closestPointOnPath(path: List<LatLng>, query: LatLng): ClosestPointResult? {
    if (path.size < 2) return null
    var best: ClosestPointResult? = null
    for (i in 0 until path.size - 1) {
        val a = path[i]
        val b = path[i + 1]
        val midLat = (a.lat + b.lat) / 2
        val scale = cos(midLat * PI / 180)
        val bx = (b.lng - a.lng) * scale
        val by = b.lat - a.lat
        val px = (query.lng - a.lng) * scale
        val py = query.lat - a.lat
        val lenSq = bx * bx + by * by
        var t = if (lenSq > 0) (px * bx + py * by) / lenSq else 0.0
        t = t.coerceIn(0.0, 1.0)
        val point = LatLng(a.lat + t * by, a.lng + t * bx / scale)
        val distanceM = haversineMeters(point, query)
        if (best == null || distanceM < best.distanceM) {
            best = ClosestPointResult(point, i, distanceM)
        }
    }
    return best
}

private const val SEGMENT_TAP_RADIUS_M = 35.0

/** Nearest canonical segment within tap radius, if any. */
fun findSegmentAtTap(
    segments: List<com.routy.app.logic.api.SegmentDto>,
    tap: LatLng,
): Pair<com.routy.app.logic.api.SegmentDto, ClosestPointResult>? {
    var best: Pair<com.routy.app.logic.api.SegmentDto, ClosestPointResult>? = null
    for (segment in segments) {
        if (!segment.isCanonical()) continue
        val path = segment.geometry.map { LatLng(it.lat, it.lng) }
        val hit = closestPointOnPath(path, tap) ?: continue
        if (hit.distanceM > SEGMENT_TAP_RADIUS_M) continue
        if (best == null || hit.distanceM < best.second.distanceM) {
            best = segment to hit
        }
    }
    return best
}
