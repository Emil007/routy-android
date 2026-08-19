package com.routy.app.logic.recording

import kotlin.test.Test
import kotlin.test.assertEquals

class RecordingLocationTest {
    @Test
    fun fastIntervalWhenFewPoints() {
        assertEquals(3_000L, recordingLocationIntervalMs(listOf(point(0.0, 0.0, 0))))
    }

    @Test
    fun fastIntervalWhenMovingQuickly() {
        val points = listOf(
            point(0.0, 0.0, 0),
            point(0.00005, 0.0, 3_000), // ~5.5 m/s over 3s
        )
        assertEquals(3_000L, recordingLocationIntervalMs(points))
    }

    @Test
    fun mediumIntervalWhenWalking() {
        val points = listOf(
            point(0.0, 0.0, 0),
            point(0.00001, 0.0, 3_000), // ~1.1 m/s over 3s
        )
        assertEquals(6_000L, recordingLocationIntervalMs(points))
    }

    @Test
    fun slowIntervalWhenStationary() {
        val points = listOf(
            point(0.0, 0.0, 0),
            point(0.000001, 0.0, 3_000), // negligible movement
        )
        assertEquals(12_000L, recordingLocationIntervalMs(points))
    }

    private fun point(lat: Double, lng: Double, timestampMs: Long) =
        RecordingPoint(lat, lng, null, timestampMs)
}
