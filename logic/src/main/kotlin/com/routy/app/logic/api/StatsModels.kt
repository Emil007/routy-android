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
data class WalkLogEntryDto(
    val id: Int,
    val nodeChain: List<Int>,
    val segmentIds: List<Int> = emptyList(),
    val lengthM: Int,
    val durationMin: Int,
    val nickname: String? = null,
    val acceptedAt: String,
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
data class AppStatsMeResponse(
    val stats: UserStatsDto,
    val streak: StreakStatsDto,
    val achievements: AchievementsDto,
    val recentWalks: List<WalkLogEntryDto>,
    val points: UserPointsDto? = null,
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

