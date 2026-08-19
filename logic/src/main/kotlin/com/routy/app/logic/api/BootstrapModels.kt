package com.routy.app.logic.api

import kotlinx.serialization.Serializable

@Serializable
data class AppBootstrapResponse(
    val user: SessionUser,
    val networkVersion: String,
    val nodes: List<NodeDto>,
    val segments: List<SegmentDto>,
    val routeState: RouteStateResponse,
    val avoidSegmentIds: List<Int> = emptyList(),
)
