package com.routy.app.core.storage

import android.content.Context
import com.routy.app.logic.cache.RouteProgress
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Persists active-route waypoint progress across process death. */
class RouteProgressStore(context: Context) {
    private val prefs = context.getSharedPreferences("route_progress", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun save(routeKey: String, completedIndex: Int, voiceAnnouncedIndex: Int) {
        prefs.edit()
            .putString("progress", json.encodeToString(RouteProgress(routeKey, completedIndex, voiceAnnouncedIndex)))
            .apply()
    }

    fun load(routeKey: String): RouteProgress? {
        val raw = prefs.getString("progress", null) ?: return null
        return runCatching { json.decodeFromString<RouteProgress>(raw) }
            .getOrNull()
            ?.takeIf { it.routeKey == routeKey }
    }

    fun clear() {
        prefs.edit().remove("progress").apply()
    }
}
