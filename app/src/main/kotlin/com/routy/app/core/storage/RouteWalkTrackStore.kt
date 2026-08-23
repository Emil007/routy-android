package com.routy.app.core.storage

import android.content.Context
import com.routy.app.logic.route.RouteWalkTrackPoint
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Persists GPX track points for an active route walk until Complete or Discard. */
class RouteWalkTrackStore(context: Context) {
    private val prefs = context.getSharedPreferences("route_walk_track", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun save(routeKey: String, points: List<RouteWalkTrackPoint>, goldenHits: Set<Int>) {
        prefs.edit()
            .putString("track", json.encodeToString(RouteWalkTrackSnapshot(routeKey, points, goldenHits.toList())))
            .apply()
    }

    fun load(routeKey: String): RouteWalkTrackSnapshot? {
        val raw = prefs.getString("track", null) ?: return null
        return runCatching { json.decodeFromString<RouteWalkTrackSnapshot>(raw) }
            .getOrNull()
            ?.takeIf { it.routeKey == routeKey }
    }

    fun clear() {
        prefs.edit().remove("track").apply()
    }
}

@kotlinx.serialization.Serializable
data class RouteWalkTrackSnapshot(
    val routeKey: String,
    val points: List<RouteWalkTrackPoint>,
    val goldenHitSegmentIds: List<Int> = emptyList(),
)
