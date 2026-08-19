package com.routy.app.logic.cache

import com.routy.app.logic.api.NodeDto
import com.routy.app.logic.api.RouteStateResponse
import com.routy.app.logic.api.SegmentDto
import com.routy.app.logic.api.SessionUser
import kotlinx.serialization.Serializable

@Serializable
data class CachedNetwork(
    val etag: String,
    val nodes: List<NodeDto>,
    val segments: List<SegmentDto>,
    val cachedAtMs: Long,
)

@Serializable
data class CachedBootstrap(
    val etag: String,
    val user: SessionUser,
    val networkVersion: String,
    val nodes: List<NodeDto>,
    val segments: List<SegmentDto>,
    val routeState: RouteStateResponse,
    val avoidSegmentIds: List<Int> = emptyList(),
    val cachedAtMs: Long,
)

@Serializable
data class RouteProgress(
    val routeKey: String,
    val completedIndex: Int,
    val voiceAnnouncedIndex: Int = 0,
)
