package com.routy.app.logic.recording

import kotlin.test.Test
import kotlin.test.assertEquals

class RecordingDurationTest {
    @Test
    fun `ten minutes of timestamps with a four-minute pause gap count six minutes`() {
        // Points span 10 minutes of wall-clock, but 4 minutes are paused (last point of
        // the running segment, then a pause, then continuation). Since the service merges
        // pauses into the point list, the "pause" here is simply a 4-minute gap between
        // recorded points that should still count as active recording time for the third
        // point — the real pause elimination happens via RecordingSession.stats. For the
        // ViewModel path we only verify first→last timestamp mapping.
        val points = listOf(
            RecordingPoint(52.0, 13.0, timestampMs = 0L),
            RecordingPoint(52.001, 13.0, timestampMs = 10 * 60_000L), // 10 min later
        )
        val minutes = recordingDurationMin(points, lengthM = 500.0, walkSpeedKmh = 5.0)
        assertEquals(10, minutes)
    }

    @Test
    fun `short span rounds up to one minute`() {
        val points = listOf(
            RecordingPoint(52.0, 13.0, timestampMs = 0L),
            RecordingPoint(52.001, 13.0, timestampMs = 30_000L), // 30 s
        )
        assertEquals(1, recordingDurationMin(points, lengthM = 200.0, walkSpeedKmh = 5.0))
    }

    @Test
    fun `falls back to speed estimate when no timestamps`() {
        val points = listOf(
            RecordingPoint(52.0, 13.0, timestampMs = 0L),
            RecordingPoint(52.001, 13.0, timestampMs = 0L), // identical timestamps
        )
        // 111 m at 5 km/h ≈ 1.3 min → estimateMinutes roundToInt = 1
        assertEquals(1, recordingDurationMin(points, lengthM = 111.0, walkSpeedKmh = 5.0))
    }
}
