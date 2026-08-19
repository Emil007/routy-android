package com.routy.app.logic.geo

import com.routy.app.logic.api.GeoPoint

/** Flatten segment geometry into one polyline for walk previews (skips duplicate junction points). */
fun walkPathPoints(
    segmentIds: List<Int>,
    geometryBySegmentId: Map<Int, List<GeoPoint>>,
    fallbackNodeChain: List<Int> = emptyList(),
    fallbackCoords: Map<Int, GeoPoint> = emptyMap(),
): List<GeoPoint> {
    val points = mutableListOf<GeoPoint>()
    for (id in segmentIds) {
        val geom = geometryBySegmentId[id] ?: continue
        if (geom.isEmpty()) continue
        if (points.isEmpty()) points.addAll(geom)
        else points.addAll(geom.drop(1))
    }
    if (points.size >= 2) return points

    val chain = fallbackNodeChain.mapNotNull { fallbackCoords[it] }
    if (chain.size >= 2) return chain
    return points
}
