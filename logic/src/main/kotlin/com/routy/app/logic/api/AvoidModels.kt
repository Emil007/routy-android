package com.routy.app.logic.api

import kotlinx.serialization.Serializable

@Serializable
data class AvoidListResponse(val segmentIds: List<Int> = emptyList())

@Serializable
data class AvoidSegmentRequest(val segmentId: Int)
