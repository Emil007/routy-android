package com.routy.app.logic.api

import kotlinx.serialization.Serializable

@Serializable
data class ShareRouteResponse(
    val name: String,
    val stale: Boolean,
    val display: RouteDisplayPayload? = null,
    val nodeChain: List<Int> = emptyList(),
    val segmentIds: List<Int> = emptyList(),
    val lengthM: Int = 0,
    val durationMin: Int = 0,
)
