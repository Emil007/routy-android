package com.routy.app.logic.time

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class ServerDateTimeTest {
    @Test
    fun `parseServerInstant accepts space separated without zone`() {
        assertEquals(Instant.parse("2024-06-01T14:30:00Z"), parseServerInstant("2024-06-01 14:30:00"))
    }

    @Test
    fun `parseServerInstant does not double append Z`() {
        assertEquals(Instant.parse("2024-06-01T14:30:00Z"), parseServerInstant("2024-06-01T14:30:00Z"))
    }

    @Test
    fun `formatDurationHours keeps fractional hours`() {
        assertEquals("1.5", formatDurationHours(90))
        assertEquals("2", formatDurationHours(120))
    }
}
