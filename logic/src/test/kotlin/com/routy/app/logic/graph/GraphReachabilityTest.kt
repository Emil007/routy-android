package com.routy.app.logic.graph

import com.routy.app.logic.api.SegmentDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GraphReachabilityTest {
    private val homeId = 1
    private val segments = listOf(
        seg(10, 1, 2),
        seg(11, 2, 3),
        seg(20, 4, 5),
    )

    @Test
    fun `reachable nodes follow connected component from home`() {
        val reachable = reachableNodeIds(segments, homeId)
        assertEquals(setOf(1, 2, 3), reachable)
    }

    @Test
    fun `disconnected segments are flagged`() {
        val disconnected = disconnectedCanonicalSegmentIds(segments, homeId)
        assertEquals(setOf(20), disconnected)
    }

    @Test
    fun `null home marks all canonical segments disconnected`() {
        val disconnected = disconnectedCanonicalSegmentIds(segments, null)
        assertTrue(disconnected.containsAll(setOf(10, 11, 20)))
    }

    private fun seg(id: Int, start: Int, end: Int) = SegmentDto(
        id = id,
        startNodeId = start,
        endNodeId = end,
        geometry = emptyList(),
        lengthM = 100,
    )
}
