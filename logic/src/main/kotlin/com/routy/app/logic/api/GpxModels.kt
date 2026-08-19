package com.routy.app.logic.api

import kotlinx.serialization.Serializable

@Serializable
data class GpxPoint(val lat: Double, val lng: Double, val ele: Double? = null)

@Serializable
data class GpxElevation(val gainM: Int, val lossM: Int, val minM: Int, val maxM: Int)

/**
 * POST /api/gpx/commit's `start`/`end` is a Zod union of `{ nodeId }` or `{ part1, part2 }` — an
 * existing junction vs. a brand-new one. Modeled here as one class with everything nullable;
 * the shared Json instance in :app is configured with explicitNulls = false, so whichever
 * fields are actually set are the only ones that reach the wire, naturally matching whichever
 * union branch applies without a custom serializer.
 */
@Serializable
data class GpxEndpoint(val nodeId: Int? = null, val part1: String? = null, val part2: String? = null) {
    companion object {
        fun existing(nodeId: Int) = GpxEndpoint(nodeId = nodeId)
        fun new(part1: String, part2: String) = GpxEndpoint(part1 = part1, part2 = part2)
    }
}

@Serializable
data class GpxTrack(
    val points: List<GpxPoint>,
    val lengthM: Int,
    val durationMin: Int,
    val elevation: GpxElevation? = null,
    val start: GpxEndpoint,
    val end: GpxEndpoint,
    val markStartAsHome: Boolean? = null,
    val source: String = "gpx", // "gpx" | "drawn" — "gpx" for anything the app records itself.
)

@Serializable
data class GpxCommitRequest(val tracks: List<GpxTrack>)

/** GET /api/gpx/config — mirrors the settings.merge_radius_m / effectiveWalkSpeedKmh props the web's /map RSC computes server-side. */
@Serializable
data class GpxConfigResponse(val mergeRadiusM: Double, val walkSpeedKmh: Double)

@Serializable
data class PendingGpxCommit(
    val id: String,
    val enqueuedAtMs: Long,
    val request: GpxCommitRequest,
)
