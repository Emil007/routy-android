package com.routy.app.logic.route

import com.routy.app.logic.api.RouteStation
import com.routy.app.logic.geo.CompassPoint
import com.routy.app.logic.geo.LatLng
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class VoiceCueTrackerTest {
    // A straight line of 3 stations, ~55m apart (just past the 50m trigger radius) heading due east.
    private val stations = listOf(
        RouteStation(nodeId = 1, name = "Home", lat = 52.0, lng = 13.0),
        RouteStation(nodeId = 2, name = "Oak Junction", lat = 52.0, lng = 13.0008),
        RouteStation(nodeId = 3, name = null, lat = 52.0, lng = 13.0016),
    )

    @Test
    fun `no cue while far from the next station`() {
        val tracker = VoiceCueTracker(stations)
        val cue = tracker.onLocationUpdate(LatLng(51.99, 13.0))
        assertNull(cue)
    }

    @Test
    fun `fires ArrivingAtNext once within radius, naming the next station and direction`() {
        val tracker = VoiceCueTracker(stations)
        val cue = tracker.onLocationUpdate(LatLng(52.0, 13.0))
        val next = assertIs<VoiceCue.ArrivingAtNext>(cue)
        assertEquals("Home", next.hereName)
        assertEquals("Oak Junction", next.nextName)
        assertEquals(CompassPoint.E, next.direction)
    }

    @Test
    fun `does not re-announce the same station on a repeated update within radius`() {
        val tracker = VoiceCueTracker(stations)
        tracker.onLocationUpdate(LatLng(52.0, 13.0))
        val second = tracker.onLocationUpdate(LatLng(52.0, 13.0))
        assertNull(second)
    }

    @Test
    fun `walks through all stations in order`() {
        val tracker = VoiceCueTracker(stations)
        tracker.onLocationUpdate(LatLng(52.0, 13.0)) // arrives at Home -> next is Oak Junction
        val cue = tracker.onLocationUpdate(LatLng(52.0, 13.0008)) // arrives at Oak Junction -> next is unnamed
        val next = assertIs<VoiceCue.ArrivingAtNext>(cue)
        assertEquals("Oak Junction", next.hereName)
        assertNull(next.nextName)
    }

    @Test
    fun `fires ArrivingAtFinal at the last station with no next`() {
        val tracker = VoiceCueTracker(stations)
        tracker.onLocationUpdate(LatLng(52.0, 13.0))
        tracker.onLocationUpdate(LatLng(52.0, 13.0008))
        val cue = tracker.onLocationUpdate(LatLng(52.0, 13.0016))
        val final = assertIs<VoiceCue.ArrivingAtFinal>(cue)
        assertNull(final.hereName)
    }

    @Test
    fun `nothing left to announce after the final station`() {
        val tracker = VoiceCueTracker(stations)
        tracker.onLocationUpdate(LatLng(52.0, 13.0))
        tracker.onLocationUpdate(LatLng(52.0, 13.0008))
        tracker.onLocationUpdate(LatLng(52.0, 13.0016))
        assertNull(tracker.onLocationUpdate(LatLng(52.0, 13.0016)))
    }

    @Test
    fun `reset starts the sequence over`() {
        val tracker = VoiceCueTracker(stations)
        tracker.onLocationUpdate(LatLng(52.0, 13.0))
        tracker.reset()
        val cue = tracker.onLocationUpdate(LatLng(52.0, 13.0))
        assertIs<VoiceCue.ArrivingAtNext>(cue)
    }

    @Test
    fun `single-station route goes straight to ArrivingAtFinal`() {
        val tracker = VoiceCueTracker(listOf(stations[0]))
        val cue = tracker.onLocationUpdate(LatLng(52.0, 13.0))
        assertIs<VoiceCue.ArrivingAtFinal>(cue)
    }
}
