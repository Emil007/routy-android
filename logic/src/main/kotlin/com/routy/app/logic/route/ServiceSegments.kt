package com.routy.app.logic.route

import com.routy.app.logic.api.SegmentDto

/**
 * M11: The route-tracking foreground service only needs the segments that belong to
 * the active route (route segments + golden-hit segments) — not the entire network.
 * Keeping the full network out of the service-start intent avoids
 * TransactionTooLargeException on large networks (the rest already lives in-process).
 */
fun segmentsForService(
    all: List<SegmentDto>,
    routeSegmentIds: List<Int>,
    goldenSegmentIds: Set<Int>,
): List<SegmentDto> {
    val wanted: MutableSet<Int> = HashSet(routeSegmentIds.size + goldenSegmentIds.size)
    wanted.addAll(routeSegmentIds)
    wanted.addAll(goldenSegmentIds)
    return all.filter { it.id in wanted }
}
