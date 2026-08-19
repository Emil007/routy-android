package com.routy.app.logic.map

import com.routy.app.logic.api.GeoPoint
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

data class GeoBounds(val minLat: Double, val minLng: Double, val maxLat: Double, val maxLng: Double)

data class TileCoord(val z: Int, val x: Int, val y: Int)

/** Bounding box for route geometry with padding — used for offline tile prefetch. */
fun boundsFromGeoPoints(points: List<GeoPoint>, paddingM: Double = 500.0): GeoBounds? {
    if (points.isEmpty()) return null
    var minLat = points.first().lat
    var maxLat = minLat
    var minLng = points.first().lng
    var maxLng = minLng
    for (p in points.drop(1)) {
        minLat = minOf(minLat, p.lat)
        maxLat = maxOf(maxLat, p.lat)
        minLng = minOf(minLng, p.lng)
        maxLng = maxOf(maxLng, p.lng)
    }
    val midLat = (minLat + maxLat) / 2.0
    val latPad = paddingM / 111_000.0
    val lngPad = paddingM / (111_000.0 * cos(midLat * Math.PI / 180.0).coerceAtLeast(0.1))
    return GeoBounds(minLat - latPad, minLng - lngPad, maxLat + latPad, maxLng + lngPad)
}

fun lonToTileX(lon: Double, zoom: Int): Int {
    val n = 1 shl zoom
    return floor((lon + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
}

fun latToTileY(lat: Double, zoom: Int): Int {
    val n = 1 shl zoom
    val latRad = Math.toRadians(lat.coerceIn(-85.0511, 85.0511))
    val y = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / Math.PI) / 2.0 * n
    return floor(y).toInt().coerceIn(0, n - 1)
}

/** Walk-scale zoom levels; capped so prefetch stays bounded on long routes. */
fun tilesForBounds(bounds: GeoBounds, minZoom: Int = 13, maxZoom: Int = 15, maxTiles: Int = 400): List<TileCoord> {
    val tiles = mutableListOf<TileCoord>()
    for (z in minZoom..maxZoom) {
        val xMin = lonToTileX(bounds.minLng, z)
        val xMax = lonToTileX(bounds.maxLng, z)
        val yMin = latToTileY(bounds.maxLat, z)
        val yMax = latToTileY(bounds.minLat, z)
        for (x in xMin..xMax) {
            for (y in yMin..yMax) {
                tiles.add(TileCoord(z, x, y))
                if (tiles.size >= maxTiles) return tiles
            }
        }
    }
    return tiles
}
