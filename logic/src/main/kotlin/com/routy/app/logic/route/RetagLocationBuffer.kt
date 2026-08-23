package com.routy.app.logic.route

import com.routy.app.logic.geo.LatLng
import com.routy.app.logic.geo.haversineMeters

data class RetagLocationSample(val lat: Double, val lng: Double, val accuracyM: Float)

private const val HIGH_ACCURACY_MAX_M = 15f
private const val MIN_SAMPLES = 3
private const val MIN_MOVE_DISTANCE_M = 20.0
private const val MAX_BUFFER = 12

/**
 * Collects recent high-accuracy GPS samples while tracking. Offers a move target only when
 * several samples cluster away from the stored node — never on a single ping (spec D7).
 */
class RetagLocationBuffer {
    private val samples = ArrayDeque<RetagLocationSample>(MAX_BUFFER)

    fun addSample(lat: Double, lng: Double, accuracyM: Float?) {
        if (accuracyM == null || accuracyM <= 0 || accuracyM > HIGH_ACCURACY_MAX_M) return
        if (samples.size >= MAX_BUFFER) samples.removeFirst()
        samples.addLast(RetagLocationSample(lat, lng, accuracyM))
    }

    /** Median cluster center when enough high-accuracy samples agree away from [stored]; else null. */
    fun clusterMoveTarget(stored: LatLng): LatLng? {
        if (samples.size < MIN_SAMPLES) return null
        val recent = samples.toList().takeLast(MIN_SAMPLES)
        val medianLat = median(recent.map { it.lat })
        val medianLng = median(recent.map { it.lng })
        val target = LatLng(medianLat, medianLng)
        if (haversineMeters(stored, target) < MIN_MOVE_DISTANCE_M) return null
        val spreadM = recent.maxOf { haversineMeters(target, LatLng(it.lat, it.lng)) }
        if (spreadM > HIGH_ACCURACY_MAX_M * 2) return null
        return target
    }

    fun clear() {
        samples.clear()
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[mid - 1] + sorted[mid]) / 2.0
        } else {
            sorted[mid]
        }
    }
}
