package com.routy.app.logic.geo

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GeoTest {
    // Berlin Alexanderplatz -> Brandenburg Gate, real-world reference distance ~2.5km.
    private val alexanderplatz = LatLng(52.5219, 13.4132)
    private val brandenburgGate = LatLng(52.5163, 13.3777)

    @Test
    fun `haversineMeters matches a known real-world distance`() {
        val distance = haversineMeters(alexanderplatz, brandenburgGate)
        assertTrue(distance in 2400.0..2600.0, "expected ~2.5km, got $distance")
    }

    @Test
    fun `haversineMeters is zero for identical points`() {
        assertEquals(0.0, haversineMeters(alexanderplatz, alexanderplatz), absoluteTolerance = 1e-9)
    }

    @Test
    fun `bearing due north is 0`() {
        val south = LatLng(52.0, 13.0)
        val north = LatLng(53.0, 13.0)
        assertTrue(abs(bearing(south, north)) < 0.5)
    }

    @Test
    fun `bearing due east is 90`() {
        val west = LatLng(52.0, 13.0)
        val east = LatLng(52.0, 14.0)
        assertTrue(abs(bearing(west, east) - 90) < 1.0)
    }

    @Test
    fun `compassDirection buckets into 8 points`() {
        assertEquals(CompassPoint.N, compassDirection(0.0))
        assertEquals(CompassPoint.N, compassDirection(359.0))
        assertEquals(CompassPoint.E, compassDirection(90.0))
        assertEquals(CompassPoint.S, compassDirection(180.0))
        assertEquals(CompassPoint.NW, compassDirection(315.0))
    }

    @Test
    fun `pathLengthMeters sums consecutive segments`() {
        val points = listOf(LatLng(52.0, 13.0), LatLng(52.001, 13.0), LatLng(52.002, 13.0))
        val total = pathLengthMeters(points)
        val leg = haversineMeters(points[0], points[1])
        assertEquals(leg * 2, total, absoluteTolerance = 0.01)
    }

    @Test
    fun `estimateMinutes falls back to 5kmh when speed is non-positive`() {
        assertEquals(estimateMinutes(5000.0, 5.0), estimateMinutes(5000.0, 0.0))
        assertEquals(estimateMinutes(5000.0, 5.0), estimateMinutes(5000.0, -3.0))
    }

    @Test
    fun `elevationStats needs at least 2 samples`() {
        assertNull(elevationStats(emptyList()))
        assertNull(elevationStats(listOf(100.0)))
    }

    @Test
    fun `elevationStats sums gain and loss separately`() {
        val stats = elevationStats(listOf(100.0, 150.0, 120.0, 140.0))
        checkNotNull(stats)
        assertEquals(70, stats.gainM) // +50, +20
        assertEquals(30, stats.lossM) // -30
        assertEquals(100, stats.minM)
        assertEquals(150, stats.maxM)
    }

    @Test
    fun `reversePoints does not mutate the input`() {
        val points = listOf(LatLng(1.0, 1.0), LatLng(2.0, 2.0))
        val reversed = reversePoints(points)
        assertEquals(listOf(LatLng(2.0, 2.0), LatLng(1.0, 1.0)), reversed)
        assertEquals(listOf(LatLng(1.0, 1.0), LatLng(2.0, 2.0)), points)
    }

    private fun assertEquals(expected: Double, actual: Double, absoluteTolerance: Double) {
        assertTrue(abs(expected - actual) <= absoluteTolerance, "expected $expected, got $actual")
    }
}
