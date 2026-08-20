package com.routy.app.logic.api

import kotlinx.serialization.Serializable

@Serializable
data class PointPreviewBreakdown(
    val base: Int = 0,
    val golden: Int = 0,
    val exploration: Int = 0,
    val diversity: Int = 0,
    val total: Int = 0,
)

@Serializable
data class GameSummaryDto(
    val totalPoints: Int = 0,
    val weeklyPoints: Int = 0,
    val streakMultiplier: Double = 1.0,
)

@Serializable
data class LockProposalDto(
    val id: Int,
    val segmentId: Int,
    val requestedBy: Int,
    val reason: String? = null,
    val days: Int = 7,
    val createdAt: String,
)

@Serializable
data class LockProposalDetailDto(
    val id: Int,
    val segmentId: Int,
    val segmentName: String? = null,
    val requestedBy: Int,
    val requesterName: String? = null,
    val reason: String? = null,
    val days: Int = 7,
    val createdAt: String,
)

@Serializable
data class LockProposalsResponse(val proposals: List<LockProposalDetailDto> = emptyList())

@Serializable
data class LockProposalActionRequest(val proposalId: Int)

@Serializable
data class GoldenSegmentDto(
    val segmentId: Int,
    val multiplier: Double,
    val name: String? = null,
)

@Serializable
data class GameDailyResponse(
    val goldenSegments: List<GoldenSegmentDto> = emptyList(),
    val dailyChallenge: String = "",
    val pointBalance: Int = 0,
    val streakMultiplier: Double = 1.0,
)
