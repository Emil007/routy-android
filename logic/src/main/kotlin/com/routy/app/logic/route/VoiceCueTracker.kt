package com.routy.app.logic.route

import com.routy.app.logic.api.RouteStation
import com.routy.app.logic.geo.CompassPoint
import com.routy.app.logic.geo.LatLng
import com.routy.app.logic.geo.bearing
import com.routy.app.logic.geo.compassDirection
import com.routy.app.logic.geo.haversineMeters

/**
 * Structured cue data only — no spoken text. Station names are nullable (an unnamed junction),
 * so rendering "Station" as a fallback and building the actual sentence is left to the :app
 * layer, which has the locale and Android string resources; :logic stays UI/i18n-agnostic.
 */
sealed interface VoiceCue {
    data class ArrivingAtNext(
        val hereName: String?,
        val hereViaSegmentName: String?,
        val nextName: String?,
        val nextViaSegmentName: String?,
        val direction: CompassPoint,
    ) : VoiceCue

    data class ArrivingAtFinal(
        val hereName: String?,
        val hereViaSegmentName: String?,
    ) : VoiceCue
}

/**
 * Voice-announcement tracker — one cue per station, dynamic announce radius, and no N+1 cue until
 * the walker has left station N's radius (spec D4/D6).
 */
class VoiceCueTracker(private val stations: List<RouteStation>) {
    private var nextIndex = 0
    /** After announcing station [nextIndex - 1], wait until the walker leaves that station's radius. */
    private var waitingToLeaveIndex: Int? = null

    fun onLocationUpdate(location: LatLng): VoiceCue? {
        if (nextIndex >= stations.size) return null

        waitingToLeaveIndex?.let { leftIdx ->
            val leftStation = stations[leftIdx]
            val leftRadius = voiceAnnounceRadiusM(leftIdx, stations)
            if (haversineMeters(location, LatLng(leftStation.lat, leftStation.lng)) <= leftRadius) {
                return null
            }
            waitingToLeaveIndex = null
        }

        val station = stations[nextIndex]
        val radius = voiceAnnounceRadiusM(nextIndex, stations)
        if (haversineMeters(location, LatLng(station.lat, station.lng)) > radius) return null

        val cue = buildCue(nextIndex)
        if (nextIndex < stations.lastIndex) {
            waitingToLeaveIndex = nextIndex
        }
        nextIndex++
        return cue
    }

    /** Re-speak the upcoming station without advancing the tracker (notification peek / D9). */
    fun peekUpcomingCue(): VoiceCue? {
        val idx = nextIndex.coerceAtMost(stations.lastIndex)
        if (idx >= stations.size) return null
        return buildCue(idx)
    }

    /** Index of the next station the walker is heading toward (for notification text). */
    fun upcomingStationIndex(): Int? = if (nextIndex < stations.size) nextIndex else null

    fun restore(announcedUpTo: Int) {
        nextIndex = announcedUpTo.coerceIn(0, stations.size)
        waitingToLeaveIndex = null
    }

    fun announcedCount(): Int = nextIndex

    fun reset() {
        nextIndex = 0
        waitingToLeaveIndex = null
    }

    private fun buildCue(stationIndex: Int): VoiceCue {
        val station = stations[stationIndex]
        val hereName = stationSpeakName(station)
        val hereVia = station.viaSegmentName
        val next = stations.getOrNull(stationIndex + 1)
        return if (next != null) {
            val direction = compassDirection(
                bearing(LatLng(station.lat, station.lng), LatLng(next.lat, next.lng)),
            )
            VoiceCue.ArrivingAtNext(
                hereName = hereName,
                hereViaSegmentName = hereVia,
                nextName = stationSpeakName(next),
                nextViaSegmentName = next.viaSegmentName,
                direction = direction,
            )
        } else {
            VoiceCue.ArrivingAtFinal(hereName = hereName, hereViaSegmentName = hereVia)
        }
    }

    private fun stationSpeakName(station: RouteStation): String? =
        station.speakName ?: station.name
}
