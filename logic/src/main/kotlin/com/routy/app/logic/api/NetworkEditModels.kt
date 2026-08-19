package com.routy.app.logic.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class NodeRenameRequest(val nodeId: Int, val part1: String, val part2: String = "")

@Serializable
data class NodeMoveRequest(val nodeId: Int, val lat: Double, val lng: Double)

@Serializable
data class NodeIdRequest(val nodeId: Int)

@Serializable
data class SegmentRenameRequest(val segmentId: Int, val name: String)

@Serializable
data class SegmentIdRequest(val segmentId: Int)

@Serializable
data class SegmentLockRequest(val segmentId: Int, val days: Int? = 7, val reason: String? = null)

@Serializable
data class SegmentGeometryRequest(val segmentId: Int, val points: List<GpxPoint>)

@Serializable
data class SegmentSplitRequest(
    val segmentId: Int,
    val lat: Double,
    val lng: Double,
    val endpoint: JsonElement,
)
