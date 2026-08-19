package com.routy.app.logic.api

import com.routy.app.logic.recording.MatchableNode
import kotlinx.serialization.Serializable

/**
 * Subset of NodeRow (src/lib/nodes.ts) the native map actually renders — GET /api/nodes already
 * excludes trashed nodes. Implements MatchableNode directly (rather than an adapter at each call
 * site) since its shape already is that contract — used as-is for the recording wizard's
 * candidate-junction matching.
 */
@Serializable
data class NodeDto(
    override val id: Int,
    override val name: String? = null,
    override val lat: Double,
    override val lng: Double,
    val isHome: Boolean = false,
    val createdBy: Int? = null,
    val namePart1Text: String? = null,
    val namePart2Text: String? = null,
) : MatchableNode

@Serializable
data class NodesResponse(val nodes: List<NodeDto>)

/**
 * Matches src/lib/geo.ts's LatLng JSON shape on the wire: a plain {lat, lng} object. This is the
 * general point format used almost everywhere server-side (e.g. SegmentDto.geometry below). One
 * deliberate exception exists — RouteDisplayPayload.geometry in RouteModels.kt is a [lat, lng]
 * tuple array instead — see GeoPointTupleListSerializer there for that one field's own decoding.
 */
@Serializable
data class GeoPoint(val lat: Double, val lng: Double)

/** Subset of SegmentRow (src/lib/segments.ts) needed to draw the network. */
@Serializable
data class SegmentDto(
    val id: Int,
    val startNodeId: Int,
    val endNodeId: Int,
    val geometry: List<GeoPoint>,
    val lengthM: Int,
    val name: String? = null,
    val reverseOf: Int? = null,
    val lockedUntil: String? = null,
    val submittedBy: Int? = null,
)

@Serializable
data class SegmentsResponse(val segments: List<SegmentDto>)

/** Segments come in forward/reverse pairs (src/lib/segments.ts's isCanonicalSegment) — draw each physical path once. */
fun SegmentDto.isCanonical(): Boolean = reverseOf == null || id < reverseOf

/** Mirrors web isLocked check — segment.lockedUntil in the future. */
fun SegmentDto.isLocked(): Boolean {
    val until = lockedUntil ?: return false
    return until > java.time.Instant.now().toString()
}
