package com.routy.app.logic.recording

import kotlin.test.Test
import kotlin.test.assertEquals

class RecordingRestoreTest {
    private val snapshot = RecordingSnapshot(
        phase = RecordingPhase.RECORDING,
        startedAtMs = 1L,
        points = listOf(RecordingPoint(1.0, 2.0, null, 10L), RecordingPoint(1.1, 2.1, null, 20L)),
        pausedPoints = listOf(RecordingPoint(1.2, 2.2, null, 30L)),
    )

    @Test
    fun `returns empty when jsonl matches snapshot count`() {
        val appended = listOf(
            RecordingPoint(1.0, 2.0, null, 10L),
            RecordingPoint(1.1, 2.1, null, 20L),
            RecordingPoint(1.2, 2.2, null, 30L),
        )
        assertEquals(emptyList(), appendedPointsAfterSnapshot(snapshot, appended))
    }

    @Test
    fun `returns tail when jsonl has points after last snapshot persist`() {
        val extra = RecordingPoint(1.3, 2.3, null, 40L)
        val appended = snapshot.points + snapshot.pausedPoints + extra
        assertEquals(listOf(extra), appendedPointsAfterSnapshot(snapshot, appended))
    }
}
