package com.routy.app.logic.route

import com.routy.app.logic.api.FreeRoamNeighbour
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class FreeRoamFallbackTest {
    private val nb = listOf(
        FreeRoamNeighbour(nodeId = 10, name = "Gasse 2", viaSegmentName = "Quergasse"),
    )

    @Test
    fun `two different reached nodes with the same neighbour line produce different texts`() {
        val via = viaWordForLocale("de")
        val a = fallbackLine("Kreuzung A", nb, via)
        val b = fallbackLine("Kreuzung B", nb, via)
        assertNotEquals(a, b)
        assertEquals(true, a.contains("Kreuzung A"))
        assertEquals(true, b.contains("Kreuzung B"))
    }

    @Test
    fun `fallback includes reached name plus via segment, localized`() {
        val de = fallbackLine("Kreuzung A", nb, viaWordForLocale("de"))
        assertEquals(true, de.contains("Kreuzung A"))
        assertEquals(true, de.contains("über Quergasse"))
        assertEquals(true, de.contains("Gasse 2"))

        val en = fallbackLine("Kreuzung A", nb, viaWordForLocale("en"))
        assertEquals(true, en.contains("via Quergasse"))
    }

    @Test
    fun `blank server text maps to fallback path`() {
        // same pattern as the ViewModel Elvis: isNullOrBlank() -> fallback
        val server = "  "
        val line = if (server.isNullOrBlank()) fallbackLine("Kreuzung A", nb, viaWordForLocale("de")) else server
        assertEquals(true, line.contains("Kreuzung A"))
    }
}
