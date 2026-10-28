package com.routy.app.logic.time

import kotlin.test.Test
import kotlin.test.assertEquals

class OpeningHoursTest {
    @Test
    fun `both bounds null means always open`() {
        assertEquals(true, isNodeOpenByRule(null, null, 0))
        assertEquals(true, isNodeOpenByRule(null, null, 1439))
    }

    @Test
    fun `exactly one bound means closed`() {
        assertEquals(false, isNodeOpenByRule(600, null, 700))
        assertEquals(false, isNodeOpenByRule(null, 1380, 700))
    }

    @Test
    fun `from equals until means always open`() {
        assertEquals(true, isNodeOpenByRule(720, 720, 100))
        assertEquals(true, isNodeOpenByRule(720, 720, 830))
    }

    @Test
    fun `normal window is half-open at the start and exclusive at the end`() {
        // 07:00–20:00 window = [420, 1200); 06:59 is closed, 07:00 open,
        // 19:59 open, 20:00 closed.
        assertEquals(false, isNodeOpenByRule(420, 1200, 419))   // 06:59 → closed
        assertEquals(true, isNodeOpenByRule(420, 1200, 420))    // 07:00 → open
        assertEquals(true, isNodeOpenByRule(420, 1200, 1199))   // 19:59 → open
        assertEquals(false, isNodeOpenByRule(420, 1200, 1200))  // 20:00 → closed
        assertEquals(false, isNodeOpenByRule(420, 1200, 1300))  // 21:40 → closed
    }

    @Test
    fun `overnight window wraps`() {
        assertEquals(true, isNodeOpenByRule(1320, 360, 1400)) // 22:00 → open
        assertEquals(true, isNodeOpenByRule(1320, 360, 350))  // 05:50 → open
        assertEquals(false, isNodeOpenByRule(1320, 360, 400)) // 06:40 → closed
    }
}
