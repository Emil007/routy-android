package com.routy.app.logic.route

import com.routy.app.logic.api.SegmentDto
import com.routy.app.logic.api.canonicalId

/**
 * When a hop into [arrivedStationIndex] finishes, returns the golden canonical id to cue
 * (once), or null. Station 0 has no arriving segment.
 */
fun goldenCanonicalForFinishedHop(
    arrivedStationIndex: Int,
    routeSegmentIds: List<Int>,
    todayGoldenCanonicalIds: Set<Int>,
    segments: List<SegmentDto>,
    alreadyCued: Set<Int>,
): Int? {
    if (arrivedStationIndex <= 0) return null
    if (arrivedStationIndex - 1 !in routeSegmentIds.indices) return null
    val segId = routeSegmentIds[arrivedStationIndex - 1]
    val byId = segments.associateBy { it.id }
    val seg = byId[segId] ?: return null
    val canon = seg.canonicalId(byId)
    if (canon !in todayGoldenCanonicalIds) return null
    if (canon in alreadyCued) return null
    return canon
}
