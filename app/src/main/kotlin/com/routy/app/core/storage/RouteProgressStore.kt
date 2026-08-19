package com.routy.app.core.storage

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Persists active-route waypoint progress across process death. */
class RouteProgressStore(context: Context) {
    private val prefs = context.getSharedPreferences("route_progress", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class Progress(
        val routeKey: String,
        val completedIndex: Int,
        val voiceAnnouncedIndex: Int = 0,
    )

    fun save(routeKey: String, completedIndex: Int, voiceAnnouncedIndex: Int) {
        prefs.edit()
            .putString("progress", json.encodeToString(Progress(routeKey, completedIndex, voiceAnnouncedIndex)))
            .apply()
    }

    fun load(routeKey: String): Progress? {
        val raw = prefs.getString("progress", null) ?: return null
        return runCatching { json.decodeFromString<Progress>(raw) }
            .getOrNull()
            ?.takeIf { it.routeKey == routeKey }
    }

    fun clear() {
        prefs.edit().remove("progress").apply()
    }
}
