package com.routy.app.logic.route

import com.routy.app.logic.geo.CompassPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FreeRoamDirectionTest {
    @Test
    fun `maps each server abbreviation to the compass point`() {
        assertEquals(CompassPoint.N, compassPointFromAbbreviation("N"))
        assertEquals(CompassPoint.NE, compassPointFromAbbreviation("NE"))
        assertEquals(CompassPoint.E, compassPointFromAbbreviation("E"))
        assertEquals(CompassPoint.SE, compassPointFromAbbreviation("SE"))
        assertEquals(CompassPoint.S, compassPointFromAbbreviation("S"))
        assertEquals(CompassPoint.SW, compassPointFromAbbreviation("SW"))
        assertEquals(CompassPoint.W, compassPointFromAbbreviation("W"))
        assertEquals(CompassPoint.NW, compassPointFromAbbreviation("NW"))
    }

    @Test
    fun `unknown or blank returns null`() {
        assertNull(compassPointFromAbbreviation("XX"))
        assertNull(compassPointFromAbbreviation(""))
        assertNull(compassPointFromAbbreviation(null))
        assertNull(compassPointFromAbbreviation("  "))
    }
}
