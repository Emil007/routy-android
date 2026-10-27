package com.routy.app.logic.route

import com.routy.app.logic.api.RouteStation
import com.routy.app.logic.geo.LatLng
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WaypointProgressTrackerTest {
    private val stations = listOf(
        RouteStation(1, "A", 52.0, 13.0),
        RouteStation(2, "B", 52.0005, 13.0),
        RouteStation(3, "C", 52.001, 13.0),
    )

    @Test
    fun `completes stations sequentially within radius`() {
        val tracker = WaypointProgressTracker(stations)
        assertEquals(0, tracker.onLocationUpdate(LatLng(52.0, 13.0)))
        assertEquals(0, tracker.completedIndex)
        assertEquals(1, tracker.onLocationUpdate(LatLng(52.0005, 13.0)))
        assertEquals(1, tracker.completedIndex)
    }

    @Test
    fun `restore skips already completed stations`() {
        val tracker = WaypointProgressTracker(stations)
        tracker.restore(1)
        assertEquals(2, tracker.nextIndexOrNull)
        assertEquals(null, tracker.onLocationUpdate(LatLng(52.0, 13.0)))
    }

    @Test
    fun `markNextPassed advances exactly one station without touching radius`() {
        val tracker = WaypointProgressTracker(stations)
        // Even though we're far away, a manual pass advances the next station.
        assertEquals(0, tracker.markNextPassed()!!)
        assertEquals(0, tracker.completedIndex)
        assertEquals(1, tracker.markNextPassed()!!)
        assertEquals(1, tracker.completedIndex)
        assertEquals(2, tracker.markNextPassed()!!)
        assertTrue(tracker.isFinalCompleted)
        // Nothing after the last station.
        assertNull(tracker.markNextPassed())
    }

    @Test
    fun `restore then markNextPassed continues from restored index`() {
        val tracker = WaypointProgressTracker(stations)
        tracker.restore(1)
        assertEquals(2, tracker.markNextPassed()!!)
        assertEquals(2, tracker.completedIndex)
    }
}
