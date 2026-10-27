package com.routy.app.logic.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Golden-matching vector fixtures (decision B) — JUnit mirror of the TS
 * `goldenHitCanonicalIds` cases in src/lib/points.test.ts. Keeping both sides on
 * the same vectors guards parity: a directed route walking a physical path in
 * either direction must count exactly ONE canonical golden hit, once.
 */
class GoldenMatchingTest {

    // Physical path A(1)↔B(2): canonical 10, reverse 11.
    //            C(3)↔D(4): canonical 20, reverse 21.
    private val segments = listOf(
        SegmentDto(id = 10, startNodeId = 1, endNodeId = 2, geometry = listOf(GeoPoint(0.0, 0.0), GeoPoint(1.0, 0.0)), lengthM = 100, reverseOf = 11),
        SegmentDto(id = 11, startNodeId = 2, endNodeId = 1, geometry = listOf(GeoPoint(1.0, 0.0), GeoPoint(0.0, 0.0)), lengthM = 100, reverseOf = 10),
        SegmentDto(id = 20, startNodeId = 3, endNodeId = 4, geometry = listOf(GeoPoint(0.0, 1.0), GeoPoint(1.0, 1.0)), lengthM = 100, reverseOf = 21),
        SegmentDto(id = 21, startNodeId = 4, endNodeId = 3, geometry = listOf(GeoPoint(1.0, 1.0), GeoPoint(0.0, 1.0)), lengthM = 100, reverseOf = 20),
    )
    private val golden = setOf(10)

    @Test
    fun `counts a forward traversal`() {
        assertEquals(setOf(10), goldenHitsOnRoute(listOf(10), golden, segments))
    }

    @Test
    fun `counts a reverse traversal as the same canonical hit`() {
        assertEquals(setOf(10), goldenHitsOnRoute(listOf(11), golden, segments))
    }

    @Test
    fun `dedupes an out-and-back (forward + reverse) to a single hit`() {
        assertEquals(setOf(10), goldenHitsOnRoute(listOf(10, 11, 10), golden, segments))
    }

    @Test
    fun `returns empty when nothing golden is walked`() {
        assertTrue(goldenHitsOnRoute(listOf(20, 21), golden, segments).isEmpty())
    }

    @Test
    fun `canonicalId resolves both directions to the lower id`() {
        val byId = segments.associateBy { it.id }
        assertEquals(10, segments[0].canonicalId(byId))
        assertEquals(10, segments[1].canonicalId(byId))
        assertEquals(20, segments[2].canonicalId(byId))
        assertEquals(20, segments[3].canonicalId(byId))
    }

    @Test
    fun `returns empty for empty route or empty golden set`() {
        assertTrue(goldenHitsOnRoute(emptyList(), golden, segments).isEmpty())
        assertTrue(goldenHitsOnRoute(listOf(10), emptySet(), segments).isEmpty())
    }
}
