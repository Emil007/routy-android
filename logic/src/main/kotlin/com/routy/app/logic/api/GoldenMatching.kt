package com.routy.app.logic.api

import kotlin.math.min

/** Canonical id for a directed segment pair (matches server `canonicalSegmentId`). */
fun SegmentDto.canonicalId(byId: Map<Int, SegmentDto>): Int {
    reverseOf?.let { return min(id, it) }
    val reverse = byId.values.firstOrNull { it.reverseOf == id }
    return if (reverse != null) min(id, reverse.id) else id
}

/** Today's golden canonical ids that appear on the route (any travel direction). */
fun goldenHitsOnRoute(
    routeSegmentIds: List<Int>,
    todayGoldenCanonicalIds: Set<Int>,
    segments: List<SegmentDto>,
): Set<Int> {
    if (todayGoldenCanonicalIds.isEmpty() || routeSegmentIds.isEmpty()) return emptySet()
    val byId = segments.associateBy { it.id }
    val hits = linkedSetOf<Int>()
    for (id in routeSegmentIds) {
        val seg = byId[id] ?: continue
        val canon = seg.canonicalId(byId)
        if (canon in todayGoldenCanonicalIds) hits.add(canon)
    }
    return hits
}
