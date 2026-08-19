package com.routy.app.logic.recording

import kotlin.test.Test
import kotlin.test.assertEquals

class GpxCommitOutcomeTest {
    @Test
    fun successWhenResponseSuccessful() {
        assertEquals(GpxCommitOutcome.Success, gpxCommitOutcome(isSuccessful = true, httpCode = 200))
    }

    @Test
    fun retryOnServerError() {
        assertEquals(GpxCommitOutcome.Retry, gpxCommitOutcome(isSuccessful = false, httpCode = 503))
    }

    @Test
    fun retryOnRateLimit() {
        assertEquals(GpxCommitOutcome.Retry, gpxCommitOutcome(isSuccessful = false, httpCode = 429))
    }

    @Test
    fun permanentFailureOnClientError() {
        assertEquals(GpxCommitOutcome.PermanentFailure, gpxCommitOutcome(isSuccessful = false, httpCode = 400))
        assertEquals(GpxCommitOutcome.PermanentFailure, gpxCommitOutcome(isSuccessful = false, httpCode = 401))
    }
}
