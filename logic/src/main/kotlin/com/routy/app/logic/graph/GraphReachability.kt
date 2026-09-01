package com.routy.app.logic.graph

import com.routy.app.logic.api.SegmentDto
import com.routy.app.logic.api.isCanonical

/** Undirected reachability from [homeNodeId] on the active segment graph. */
fun reachableNodeIds(segments: List<SegmentDto>, homeNodeId: Int?): Set<Int> {
    val reachable = mutableSetOf<Int>()
    if (homeNodeId == null) return reachable
    val undirected = mutableMapOf<Int, MutableList<Int>>()
    for (seg in segments) {
        if (!seg.isCanonical() || seg.deletedAt != null) continue
        undirected.getOrPut(seg.startNodeId) { mutableListOf() }.add(seg.endNodeId)
        undirected.getOrPut(seg.endNodeId) { mutableListOf() }.add(seg.startNodeId)
    }
    val queue = ArrayDeque<Int>()
    queue.add(homeNodeId)
    reachable.add(homeNodeId)
    while (queue.isNotEmpty()) {
        val n = queue.removeFirst()
        for (next in undirected[n].orEmpty()) {
            if (reachable.add(next)) queue.add(next)
        }
    }
    return reachable
}

/** Canonical segment ids whose endpoints are not both reachable from home. */
fun disconnectedCanonicalSegmentIds(
    segments: List<SegmentDto>,
    homeNodeId: Int?,
): Set<Int> {
    val nodes = reachableNodeIds(segments, homeNodeId)
    if (nodes.isEmpty()) {
        return segments.filter { it.isCanonical() && it.deletedAt == null }.map { it.id }.toSet()
    }
    return segments
        .filter { it.isCanonical() && it.deletedAt == null }
        .filter { seg -> !nodes.contains(seg.startNodeId) || !nodes.contains(seg.endNodeId) }
        .map { it.id }
        .toSet()
}
