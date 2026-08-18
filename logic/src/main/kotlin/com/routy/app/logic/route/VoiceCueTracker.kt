package com.routy.app.logic.route

import com.routy.app.logic.api.RouteStation
import com.routy.app.logic.geo.CompassPoint
import com.routy.app.logic.geo.LatLng
import com.routy.app.logic.geo.bearing
import com.routy.app.logic.geo.compassDirection
import com.routy.app.logic.geo.haversineMeters

private const val VOICE_ANNOUNCE_RADIUS_M = 50.0

/**
 * Structured cue data only — no spoken text. Station names are nullable (an unnamed junction),
 * so rendering "Station" as a fallback and building the actual sentence is left to the :app
 * layer, which has the locale and Android string resources; :logic stays UI/i18n-agnostic.
 */
sealed interface VoiceCue {
    data class ArrivingAtNext(val hereName: String?, val nextName: String?, val direction: CompassPoint) : VoiceCue
    data class ArrivingAtFinal(val hereName: String?) : VoiceCue
}

/**
 * Kotlin port of the voice-announcement effect in src/components/RouteGenerator.tsx: walks
 * through a route's stations in order, firing exactly one cue per station the first time the
 * walker comes within [VOICE_ANNOUNCE_RADIUS_M] of it. Stateful and sequential on purpose —
 * mirrors the web's `announcedStationIndexRef`, so re-entering a station's radius (e.g.
 * doubling back) never re-announces it.
 */
class VoiceCueTracker(private val stations: List<RouteStation>) {
    private var nextIndex = 0

    /** Feed every location update while voice guidance is on; returns a cue at most once per station, in route order. */
    fun onLocationUpdate(location: LatLng): VoiceCue? {
        if (nextIndex >= stations.size) return null
        val station = stations[nextIndex]
        val distance = haversineMeters(location, LatLng(station.lat, station.lng))
        if (distance > VOICE_ANNOUNCE_RADIUS_M) return null

        nextIndex++
        val next = stations.getOrNull(nextIndex)
        return if (next != null) {
            val direction = compassDirection(bearing(LatLng(station.lat, station.lng), LatLng(next.lat, next.lng)))
            VoiceCue.ArrivingAtNext(station.name, next.name, direction)
        } else {
            VoiceCue.ArrivingAtFinal(station.name)
        }
    }

    /** Call when a new route is accepted/started — a fresh route means a fresh announcement sequence. */
    fun reset() {
        nextIndex = 0
    }
}
