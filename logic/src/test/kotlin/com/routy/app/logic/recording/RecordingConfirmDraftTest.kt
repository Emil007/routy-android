package com.routy.app.logic.recording

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecordingConfirmDraftTest {
    private val draft = RecordingConfirmDraft(
        points = listOf(
            RecordingPoint(1.0, 2.0, null, 0),
            RecordingPoint(1.1, 2.1, null, 1),
        ),
        startDecision = EndpointDecision.Existing(1),
        endDecision = EndpointDecision.Existing(2),
        markStartAsHome = true,
    )

    @Test
    fun matchesTrackWhenEndpointsMatch() {
        val track = listOf(
            RecordingPoint(1.0, 2.0, null, 99),
            RecordingPoint(1.1, 2.1, null, 100),
        )
        assertTrue(draft.matchesTrack(track))
    }

    @Test
    fun rejectsDifferentEndpoints() {
        val track = listOf(
            RecordingPoint(9.0, 2.0, null, 0),
            RecordingPoint(1.1, 2.1, null, 1),
        )
        assertFalse(draft.matchesTrack(track))
    }
}
