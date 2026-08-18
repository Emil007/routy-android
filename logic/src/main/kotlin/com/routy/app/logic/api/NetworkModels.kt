package com.routy.app.logic.api

import kotlinx.serialization.Serializable

/** Subset of NodeRow (src/lib/nodes.ts) the native map actually renders — GET /api/nodes already excludes trashed nodes. */
@Serializable
data class NodeDto(val id: Int, val name: String? = null, val lat: Double, val lng: Double, val isHome: Boolean = false)

@Serializable
data class NodesResponse(val nodes: List<NodeDto>)

/** [lat, lng] pairs, matching src/lib/geo.ts's LatLng JSON shape on the wire. */
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
)

@Serializable
data class SegmentsResponse(val segments: List<SegmentDto>)

/** Segments come in forward/reverse pairs (src/lib/segments.ts's isCanonicalSegment) — draw each physical path once. */
fun SegmentDto.isCanonical(): Boolean = reverseOf == null || id < reverseOf
