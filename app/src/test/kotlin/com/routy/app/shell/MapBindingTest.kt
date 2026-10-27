package com.routy.app.shell

import com.routy.app.logic.recording.RecordingPhase
import com.routy.app.map.MapMode
import com.routy.app.route.MapCompassMode
import com.routy.app.route.RouteMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MapBindingTest {

    private fun explore() = ExploreMapContext(mode = MapMode.View)

    @Test
    fun `record - live phase shows track in track brown without clicks`() {
        val rec = RecordingContext(phase = RecordingPhase.RECORDING, myLocation = null, points = emptyList())
        val args = mapBindingFor(
            chromeMode = ShellMode.RECORD,
            mapMode = MapMode.View,
            routeCtx = null,
            explore = explore(),
            recording = rec,
        )
        assertEquals(MapBindingKind.RECORD_TRACK, args.kind)
        assertEquals("#9a3b29", args.routeColor)
        assertFalse(args.onMapClick)
        assertEquals("record-0", args.fitKey)
    }

    @Test
    fun `plan - record is a separate destination in J2 so it does not steal the map`() {
        // In J2 lebt die Aufnahme noch als eigene Destination (RecordingScreen); der
        // RECORD-Modus der Hülle zeigt den Track erst ab J4. PE. Plan bleibt Netz.
        val rec = RecordingContext(phase = RecordingPhase.PAUSED, points = emptyList())
        val args = mapBindingFor(ShellMode.PLAN, MapMode.View, null, explore(), rec)
        assertEquals(MapBindingKind.NETWORK, args.kind)
        assertTrue(args.emphasizeNetworkSegments)
    }

    @Test
    fun `go - active route shows route args with follow and compass`() {
        val route = RouteMapContext(
            mode = RouteMode.ACTIVE,
            route = null,
            myLocation = null,
            followEnabled = true,
            compassMode = MapCompassMode.HEADING_UP,
            fitKey = "token",
        )
        val args = mapBindingFor(ShellMode.GO, MapMode.View, route, explore(), RecordingContext())
        assertEquals(MapBindingKind.ROUTE, args.kind)
        assertTrue(args.followEnabled)
        assertEquals(MapCompassMode.HEADING_UP, args.compassMode)
        assertTrue(args.fitToRouteOnly)
        assertTrue(args.onMapLongClick)
    }

    @Test
    fun `plan - J2 active route still lives in plan sheet - route wins`() {
        val route = RouteMapContext(mode = RouteMode.ACTIVE, route = null, fitKey = "token")
        val args = mapBindingFor(ShellMode.PLAN, MapMode.View, route, explore(), RecordingContext())
        assertEquals(MapBindingKind.ROUTE, args.kind)
        assertTrue(args.onMapClick) // planning clicks still allowed in J2
    }

    @Test
    fun `plan - no route falls back to network`() {
        val args = mapBindingFor(ShellMode.PLAN, MapMode.View, null, explore(), RecordingContext())
        assertEquals(MapBindingKind.NETWORK, args.kind)
        assertTrue(args.emphasizeNetworkSegments)
    }

    @Test
    fun `explore - draws overlay but stays network`() {
        val explore = ExploreMapContext(mode = MapMode.Draw)
        val args = mapBindingFor(ShellMode.EXPLORE, MapMode.Draw, null, explore, RecordingContext())
        assertEquals(MapBindingKind.NETWORK, args.kind)
        assertTrue(args.onMapClick)
    }

    @Test
    fun `explore - gpx swallows map clicks`() {
        val explore = ExploreMapContext(mode = MapMode.Gpx, onMapClick = false)
        val args = mapBindingFor(ShellMode.EXPLORE, MapMode.Gpx, null, explore, RecordingContext())
        assertEquals(MapBindingKind.NETWORK, args.kind)
        assertFalse(args.onMapClick)
    }

    @Test
    fun `record - idle phase falls back to network`() {
        val args = mapBindingFor(ShellMode.RECORD, MapMode.View, null, explore(), RecordingContext())
        assertEquals(MapBindingKind.NETWORK, args.kind)
    }
}
