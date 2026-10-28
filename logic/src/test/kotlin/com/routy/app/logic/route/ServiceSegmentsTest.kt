package com.routy.app.logic.route

import com.routy.app.logic.api.GeoPoint
import com.routy.app.logic.api.SegmentDto
import kotlin.test.Test
import kotlin.test.assertEquals

class ServiceSegmentsTest {
    private fun seg(id: Int) = SegmentDto(
        id = id,
        startNodeId = 1,
        endNodeId = 2,
        geometry = listOf(GeoPoint(48.0, 16.0), GeoPoint(48.001, 16.001)),
        lengthM = 100,
        durationMin = 1,
        name = null,
        reverseOf = null,
        lockedUntil = null,
        submittedBy = null,
        deletedAt = null,
        oneWay = false,
    )

    @Test
    fun `route ids included, network-only ids excluded`() {
        val all = listOf(seg(1), seg(2), seg(3), seg(4))
        val result = segmentsForService(all, routeSegmentIds = listOf(1, 2), goldenSegmentIds = setOf(3))
        assertEquals(listOf(1, 2, 3), result.map { it.id })
    }

    @Test
    fun `id outside route and golden is not in the list`() {
        val all = listOf(seg(1), seg(5))
        val result = segmentsForService(all, routeSegmentIds = listOf(1), goldenSegmentIds = emptySet())
        assertEquals(listOf(1), result.map { it.id })
    }
}
