package com.routy.app.logic.route

import com.routy.app.logic.api.SegmentDto
import com.routy.app.logic.geo.LatLng
import com.routy.app.logic.geo.closestPointOnPath

private const val GOLDEN_HIT_RADIUS_M = 25.0

/** First time the walker comes within range of a golden segment — for UI hit-set only (no sound). */
class GoldenSegmentHitTracker(
    goldenHitSegmentIds: Set<Int>,
    segments: List<SegmentDto>,
) {
    private val polylines: Map<Int, List<LatLng>> = goldenHitSegmentIds
        .mapNotNull { id ->
            val seg = segments.find { it.id == id } ?: return@mapNotNull null
            if (seg.geometry.size < 2) return@mapNotNull null
            id to seg.geometry.map { LatLng(it.lat, it.lng) }
        }
        .toMap()

    private val hitIds = mutableSetOf<Int>()

    /** Segment id when first hit, else null. Sound is played on hop finish, not here. */
    fun onLocation(location: LatLng): Int? {
        for ((id, path) in polylines) {
            if (id in hitIds) continue
            val dist = closestPointOnPath(path, location)?.distanceM ?: continue
            if (dist <= GOLDEN_HIT_RADIUS_M) {
                hitIds.add(id)
                return id
            }
        }
        return null
    }

    fun restore(alreadyHit: Set<Int>) {
        hitIds.clear()
        hitIds.addAll(alreadyHit.intersect(polylines.keys))
    }

    fun hitSegmentIds(): Set<Int> = hitIds.toSet()
}
