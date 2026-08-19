package com.routy.app.stats

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routy.app.R
import com.routy.app.core.StatsInvalidation
import com.routy.app.core.BootstrapLoader
import com.routy.app.core.network.ApiClientProvider
import com.routy.app.core.BootstrapResult
import com.routy.app.logic.api.AchievementsDto
import com.routy.app.logic.api.LeaderboardEntryDto
import com.routy.app.logic.api.PointsLeaderboardEntryDto
import com.routy.app.logic.api.StreakStatsDto
import com.routy.app.logic.api.UserPointsDto
import com.routy.app.logic.api.SegmentUsageStat
import com.routy.app.logic.api.UserStatsDto
import com.routy.app.logic.api.WalkLogEntryDto
import com.routy.app.logic.api.WalkLogIdRequest
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import com.routy.app.widget.WidgetUpdater
import com.routy.app.core.storage.NetworkCache
import com.routy.app.logic.api.GeoPoint

data class StatsUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val offlineCached: Boolean = false,
    val stats: UserStatsDto? = null,
    val streak: StreakStatsDto? = null,
    val achievements: AchievementsDto? = null,
    val recentWalks: List<WalkLogEntryDto> = emptyList(),
    val leaderboard: List<LeaderboardEntryDto> = emptyList(),
    val pointsLeaderboard: List<PointsLeaderboardEntryDto> = emptyList(),
    val points: UserPointsDto? = null,
    val networkUsage: List<SegmentUsageStat> = emptyList(),
    val currentUserId: Int? = null,
    val nodeCoords: Map<Int, GeoPoint> = emptyMap(),
    val segmentGeometry: Map<Int, List<GeoPoint>> = emptyMap(),
    val deletingWalkId: Int? = null,
    val messageRes: Int? = null,
)

class StatsViewModel(
    private val apiClientProvider: ApiClientProvider,
    private val networkCache: NetworkCache,
    private val bootstrapLoader: BootstrapLoader,
    private val appContext: Context,
) : ViewModel() {
    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            StatsInvalidation.version.drop(1).collect { refresh() }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val bootstrapResult = bootstrapLoader.load()
            val offline = bootstrapResult is BootstrapResult.CachedOnly
            val service = apiClientProvider.service
            try {
                val meResponse = service.appStatsMe()
                val boardResponse = runCatching { service.weeklyLeaderboard() }.getOrNull()
                val pointsBoard = runCatching { service.pointsLeaderboard() }.getOrNull()
                if (meResponse.isSuccessful) {
                    val body = meResponse.body()
                    val cached = networkCache.load()
                    val coords = cached?.nodes?.associate { it.id to GeoPoint(it.lat, it.lng) }.orEmpty()
                    val geometry = cached?.segments?.associate { it.id to it.geometry }.orEmpty()
                    _uiState.value = StatsUiState(
                        loading = false,
                        offlineCached = offline,
                        stats = body?.stats,
                        streak = body?.streak,
                        achievements = body?.achievements,
                        recentWalks = body?.recentWalks.orEmpty(),
                        leaderboard = boardResponse?.takeIf { it.isSuccessful }?.body()?.leaderboard.orEmpty(),
                        currentUserId = boardResponse?.body()?.userId,
                        points = body?.points,
                        networkUsage = body?.networkUsage.orEmpty(),
                        pointsLeaderboard = pointsBoard?.takeIf { it.isSuccessful }?.body()?.leaderboard.orEmpty(),
                        nodeCoords = coords,
                        segmentGeometry = geometry,
                    )
                    val streak = body?.streak?.currentStreak ?: 0
                    val km = (body?.stats?.totalLengthM ?: 0) / 1000.0
                    WidgetUpdater.apply(appContext, streak, km)
                } else {
                    _uiState.value = _uiState.value.copy(loading = false, error = true, offlineCached = offline)
                }
            } catch (_: IOException) {
                _uiState.value = _uiState.value.copy(loading = false, error = true, offlineCached = offline)
            }
        }
    }

    fun deleteWalk(walkId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(deletingWalkId = walkId, messageRes = null)
            val response = try {
                apiClientProvider.service.deleteWalk(WalkLogIdRequest(walkId))
            } catch (_: IOException) {
                _uiState.value = _uiState.value.copy(deletingWalkId = null, messageRes = R.string.common_error)
                return@launch
            }
            if (response.isSuccessful) {
                StatsInvalidation.bump()
                refresh()
            } else {
                _uiState.value = _uiState.value.copy(deletingWalkId = null, messageRes = R.string.common_error)
            }
        }
    }
}
