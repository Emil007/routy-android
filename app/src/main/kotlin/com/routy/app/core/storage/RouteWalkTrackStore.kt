package com.routy.app.core.storage

import android.content.Context
import com.routy.app.logic.route.FREE_ROAM_ROUTE_KEY
import com.routy.app.logic.route.RouteWalkTrackPoint
import com.routy.app.logic.route.RouteWalkTrackSnapshot
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Persists GPX track points for an active route walk until Complete or Discard. */
class RouteWalkTrackStore(context: Context) {
    private val prefs = context.getSharedPreferences("route_walk_track", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * M10: The free-roam snapshot also carries the session token + start time. Anyone writing
     * a free-roam snapshot only supplies the fields it owns (usually points); the store bridges
     * the missing token/start via loadLatest() so the service (points) and the ViewModel
     * (token/start) never clobber each other. Route keys never carry free-roam session data.
     */
    fun save(
        routeKey: String,
        points: List<RouteWalkTrackPoint>,
        goldenHits: Set<Int>,
        freeRoamToken: String? = null,
        freeRoamStartedAtMs: Long? = null,
    ) {
        val isFreeRoam = routeKey == FREE_ROAM_ROUTE_KEY
        val existing = loadLatest()
        val token = if (isFreeRoam) freeRoamToken ?: existing?.freeRoamToken else null
        val startedAt = if (isFreeRoam) freeRoamStartedAtMs ?: existing?.freeRoamStartedAtMs else null
        prefs.edit()
            .putString(
                "track",
                json.encodeToString(
                    RouteWalkTrackSnapshot.serializer(),
                    RouteWalkTrackSnapshot(routeKey, points, goldenHits.toList(), token, startedAt),
                ),
            )
            .apply()
    }

    /** M10: push an updated free-roam token into the persisted free-roam snapshot (token rotation on checkin). */
    fun saveFreeRoamToken(token: String) {
        val current = loadLatest()?.takeIf { it.routeKey == FREE_ROAM_ROUTE_KEY } ?: return
        prefs.edit()
            .putString("track", json.encodeToString(RouteWalkTrackSnapshot.serializer(), current.copy(freeRoamToken = token)))
            .apply()
    }

    fun load(routeKey: String): RouteWalkTrackSnapshot? {
        val raw = prefs.getString("track", null) ?: return null
        return runCatching { json.decodeFromString(RouteWalkTrackSnapshot.serializer(), raw) }
            .getOrNull()
            ?.takeIf { it.routeKey == routeKey }
    }

    /** M10: Loads the single persisted snapshot regardless of route key (free-roam restore after process death). */
    fun loadLatest(): RouteWalkTrackSnapshot? {
        val raw = prefs.getString("track", null) ?: return null
        return runCatching { json.decodeFromString(RouteWalkTrackSnapshot.serializer(), raw) }.getOrNull()
    }

    fun clear() {
        prefs.edit().remove("track").apply()
    }
}
