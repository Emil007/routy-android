package com.routy.app.logic.route

import kotlinx.serialization.Serializable

/** One GPS fix while route Track is on — mirrors POST /api/route/complete trackPoints. */
@Serializable
data class RouteWalkTrackPoint(
    val lat: Double,
    val lng: Double,
    val ele: Double? = null,
    /** ISO-8601 UTC timestamp. */
    val time: String? = null,
    val accuracy: Double? = null,
    val speed: Double? = null,
    val bearing: Double? = null,
)

/** In-progress GPX track for an active walk, persisted until Complete or Discard. */
@Serializable
data class RouteWalkTrackSnapshot(
    val routeKey: String,
    val points: List<RouteWalkTrackPoint>,
    val goldenHitSegmentIds: List<Int> = emptyList(),
)
