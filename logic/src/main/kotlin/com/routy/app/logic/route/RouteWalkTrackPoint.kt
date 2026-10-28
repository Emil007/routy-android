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
    /** M10: free-roam session token, persisted with the track so the round survives process death. */
    val freeRoamToken: String? = null,
    /** M10: free-roam round start (epoch-ms) — keeps durationMin correct after process death. */
    val freeRoamStartedAtMs: Long? = null,
)

/** M10: stable store key for an in-progress free-roam round (survives process death). */
const val FREE_ROAM_ROUTE_KEY = "free-roam"
