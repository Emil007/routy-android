package com.routy.app.logic.recording

import com.routy.app.logic.geo.LatLng
import com.routy.app.logic.geo.haversineMeters
import kotlinx.serialization.Serializable

/** Port of src/lib/nodeMatching.ts's MatchableNode — anything with an id/name/position. */
interface MatchableNode {
    val id: Int
    val name: String?
    val lat: Double
    val lng: Double
}

data class NodeCandidate(val id: Int, val name: String?, val lat: Double, val lng: Double, val distanceM: Double)

@Serializable
sealed interface EndpointDecision {
    @Serializable
    data class Existing(val nodeId: Int) : EndpointDecision

    @Serializable
    data class NewJunction(val part1: String = "", val part2: String = "") : EndpointDecision
}

/** Nearby existing junctions for a recorded track's start/end point, closest first. */
fun <T : MatchableNode> findNodeCandidates(nodes: List<T>, point: LatLng, radiusM: Double): List<NodeCandidate> =
    nodes
        .map { NodeCandidate(it.id, it.name, it.lat, it.lng, haversineMeters(point, LatLng(it.lat, it.lng))) }
        .filter { it.distanceM <= radiusM }
        .sortedBy { it.distanceM }

/**
 * Port of RecordTrackWizard.tsx's initialEndpointDecision: default to the closest existing
 * junction within the network's merge radius, or prompt for a new junction name if nothing's
 * close enough. The user can always override this in the confirm step.
 */
fun <T : MatchableNode> initialEndpointDecision(point: LatLng, nodes: List<T>, mergeRadiusM: Double): EndpointDecision {
    val candidates = findNodeCandidates(nodes, point, mergeRadiusM)
    val closest = candidates.firstOrNull() ?: return EndpointDecision.NewJunction()
    return EndpointDecision.Existing(closest.id)
}
