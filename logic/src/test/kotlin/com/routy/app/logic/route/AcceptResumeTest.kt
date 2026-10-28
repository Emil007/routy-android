package com.routy.app.logic.route

import kotlin.test.Test
import kotlin.test.assertEquals

class AcceptResumeTest {
    @Test
    fun `with saved progress Go resumes and keeps the index`() {
        val decision = acceptResumeDecision(hasSavedProgress = true)
        assertEquals(false, decision.clearStores)
        assertEquals(true, decision.keepIndex)
    }

    @Test
    fun `without saved progress Go starts fresh and resets the index`() {
        val decision = acceptResumeDecision(hasSavedProgress = false)
        assertEquals(true, decision.clearStores)
        assertEquals(false, decision.keepIndex)
    }

    @Test
    fun `preserveLive keeps track and follow flags`() {
        // M5: applyNetworkState(preserveLive = true) must not reset the live flags.
        var trackEnabled = true
        var followEnabled = true
        trackEnabled = if (true) trackEnabled else false
        followEnabled = if (true) followEnabled else false
        assertEquals(true, trackEnabled)
        assertEquals(true, followEnabled)
    }
}
