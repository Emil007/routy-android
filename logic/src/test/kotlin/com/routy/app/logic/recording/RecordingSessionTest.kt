package com.routy.app.logic.recording

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RecordingSessionTest {
    @Test
    fun `starts idle and accumulates points only while recording`() {
        val session = RecordingSession()
        assertEquals(RecordingPhase.IDLE, session.phase)

        // Points added before start() are silently dropped, not queued.
        session.addPoint(RecordingPoint(52.0, 13.0, timestampMs = 0))
        assertTrue(session.points.isEmpty())

        session.start(atMs = 1000)
        session.addPoint(RecordingPoint(52.0, 13.0, timestampMs = 1000))
        session.addPoint(RecordingPoint(52.0001, 13.0, timestampMs = 2000))
        assertEquals(2, session.points.size)
    }

    @Test
    fun `points arriving while paused are dropped, not buffered for later`() {
        val session = RecordingSession()
        session.start(atMs = 0)
        session.addPoint(RecordingPoint(52.0, 13.0, timestampMs = 0))
        session.pause()
        session.addPoint(RecordingPoint(52.001, 13.0, timestampMs = 5000)) // dropped
        assertEquals(1, session.points.size)
        session.resume()
        session.addPoint(RecordingPoint(52.002, 13.0, timestampMs = 6000))
        assertEquals(2, session.points.size)
    }

    @Test
    fun `finish moves to CONFIRM and keeps the recorded points`() {
        val session = RecordingSession()
        session.start(atMs = 0)
        session.addPoint(RecordingPoint(52.0, 13.0, timestampMs = 0))
        session.finish()
        assertEquals(RecordingPhase.CONFIRM, session.phase)
        assertEquals(1, session.points.size)
    }

    @Test
    fun `discard clears everything back to IDLE`() {
        val session = RecordingSession()
        session.start(atMs = 0)
        session.addPoint(RecordingPoint(52.0, 13.0, timestampMs = 0))
        session.discard()
        assertEquals(RecordingPhase.IDLE, session.phase)
        assertTrue(session.points.isEmpty())
    }

    @Test
    fun `illegal transitions throw`() {
        val session = RecordingSession()
        assertFailsWith<IllegalStateException> { session.pause() } // can't pause before starting
        assertFailsWith<IllegalStateException> { session.finish() } // can't finish before starting
    }

    @Test
    fun `stats uses wall-clock duration between first and last point when available`() {
        val session = RecordingSession()
        session.start(atMs = 0)
        session.addPoint(RecordingPoint(52.0, 13.0, timestampMs = 0))
        session.addPoint(RecordingPoint(52.001, 13.0, timestampMs = 120_000)) // 2 minutes later
        val stats = session.stats(walkSpeedKmhFallback = 5.0)
        assertEquals(2, stats.durationMinutes)
        assertTrue(stats.lengthM > 0)
    }

    @Test
    fun `stats falls back to speed-based estimate with fewer than 2 points`() {
        val session = RecordingSession()
        session.start(atMs = 0)
        session.addPoint(RecordingPoint(52.0, 13.0, timestampMs = 0))
        val stats = session.stats(walkSpeedKmhFallback = 5.0)
        assertEquals(0.0, stats.lengthM)
    }

    @Test
    fun `elevation needs at least 2 samples with elevation data`() {
        val session = RecordingSession()
        session.start(atMs = 0)
        session.addPoint(RecordingPoint(52.0, 13.0, ele = 100.0, timestampMs = 0))
        assertEquals(null, session.elevation())

        session.addPoint(RecordingPoint(52.001, 13.0, ele = 140.0, timestampMs = 60_000))
        val stats = session.elevation()
        assertEquals(40, stats?.gainM)
    }
}
