package com.routy.app.logic.geo

import com.routy.app.logic.api.GeoPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WalkPathPointsTest {
    @Test
    fun chainsSegmentGeometryWithoutDuplicatingJunctionPoints() {
        val geometry = mapOf(
            1 to listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 1.0), GeoPoint(0.0, 2.0)),
            2 to listOf(GeoPoint(0.0, 2.0), GeoPoint(1.0, 2.0)),
        )
        val path = walkPathPoints(listOf(1, 2), geometry)
        assertEquals(
            listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 1.0), GeoPoint(0.0, 2.0), GeoPoint(1.0, 2.0)),
            path,
        )
    }

    @Test
    fun fallsBackToNodeChainWhenGeometryMissing() {
        val path = walkPathPoints(
            segmentIds = emptyList(),
            geometryBySegmentId = emptyMap(),
            fallbackNodeChain = listOf(10, 11),
            fallbackCoords = mapOf(10 to GeoPoint(5.0, 5.0), 11 to GeoPoint(6.0, 6.0)),
        )
        assertEquals(listOf(GeoPoint(5.0, 5.0), GeoPoint(6.0, 6.0)), path)
    }
}
