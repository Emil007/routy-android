package com.routy.app.logic.api

import kotlinx.serialization.Serializable

@Serializable
data class UserStatsDto(
    val walkCount: Int,
    val totalLengthM: Int,
    val totalDurationMin: Int,
    val segmentsExplored: Int,
    val totalSegments: Int,
)

@Serializable
data class StreakStatsDto(
    val currentStreak: Int,
    val longestStreak: Int,
)

@Serializable
data class WalkLogIdRequest(val walkId: Int)

@Serializable
data class WalkLogEntryDto(
    val id: Int,
    val nodeChain: List<Int>,
    val segmentIds: List<Int> = emptyList(),
    val lengthM: Int,
    val durationMin: Int,
    val nickname: String? = null,
    val acceptedAt: String,
    val pointsEarned: Int? = null,
    val pointsBase: Int? = null,
    val pointsGolden: Int? = null,
    val pointsExploration: Int? = null,
    val pointsDiversity: Int? = null,
    val streakMultiplier: Double? = null,
    val celebrationTier: String? = null,
    val goldenHits: Int? = null,
)

@Serializable
data class LeaderboardEntryDto(
    val userId: Int,
    val displayName: String,
    val totalLengthM: Int,
    val walkCount: Int,
)

@Serializable
data class AchievementsDto(
    val scalable: List<ScalableAchievementDto>,
    val special: List<SpecialAchievementDto>,
)

@Serializable
data class ScalableAchievementDto(
    val category: String,
    val categoryLabel: String,
    val tierIndex: Int,
    val tierLabel: String? = null,
    val progressLabel: String,
)

@Serializable
data class SpecialAchievementDto(
    val id: String,
    val label: String,
    val description: String,
    val earned: Boolean,
)

@Serializable
data class UserPointsDto(
    val totalPoints: Int,
    val weeklyPoints: Int,
    val streakMultiplier: Double,
)

@Serializable
data class PointsLeaderboardEntryDto(
    val userId: Int,
    val displayName: String,
    val totalPoints: Int,
)

@Serializable
data class SegmentUsageStat(
    val segmentId: Int,
    val startNodeId: Int,
    val endNodeId: Int,
    val usageCount: Int,
)

@Serializable
data class AppStatsMeResponse(
    val stats: UserStatsDto,
    val streak: StreakStatsDto,
    val achievements: AchievementsDto,
    val recentWalks: List<WalkLogEntryDto>,
    val points: UserPointsDto? = null,
    val networkUsage: List<SegmentUsageStat> = emptyList(),
)

@Serializable
data class WeeklyLeaderboardResponse(
    val userId: Int,
    val leaderboard: List<LeaderboardEntryDto>,
)

@Serializable
data class PointsLeaderboardResponse(
    val userId: Int,
    val leaderboard: List<PointsLeaderboardEntryDto>,
)

