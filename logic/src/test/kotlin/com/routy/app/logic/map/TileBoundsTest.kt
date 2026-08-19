package com.routy.app.logic.map

import com.routy.app.logic.api.GeoPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TileBoundsTest {
    @Test
    fun boundsFromGeoPointsAddsPadding() {
        val bounds = boundsFromGeoPoints(listOf(GeoPoint(50.0, 8.0), GeoPoint(50.01, 8.01)), paddingM = 500.0)
        assertNotNull(bounds)
        assertTrue(bounds.minLat < 50.0)
        assertTrue(bounds.maxLat > 50.01)
    }

    @Test
    fun tilesForBoundsRespectsCap() {
        val bounds = GeoBounds(49.0, 7.0, 51.0, 9.0)
        assertEquals(400, tilesForBounds(bounds, maxTiles = 400).size)
    }

    @Test
    fun hikingTileUrlUsesSubdomain() {
        assertTrue(tileUrl(MapTileStyle.HIKING, 14, 10, 20).contains("tile.opentopomap.org"))
    }
}
