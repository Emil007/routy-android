package com.routy.app.logic.api

import kotlinx.serialization.Serializable

@Serializable
data class SegmentConditionDto(
    val id: Int,
    val segmentId: Int,
    val reason: String,
    val reportedBy: Int,
    val expiresAt: String,
)

@Serializable
data class ReportConditionRequest(
    val segmentId: Int,
    val reason: String,
)

@Serializable
data class ReportConditionResponse(val condition: SegmentConditionDto)
