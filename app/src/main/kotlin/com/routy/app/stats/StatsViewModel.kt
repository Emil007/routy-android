package com.routy.app.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routy.app.core.network.ApiClientProvider
import com.routy.app.logic.api.AchievementsDto
import com.routy.app.logic.api.LeaderboardEntryDto
import com.routy.app.logic.api.StreakStatsDto
import com.routy.app.logic.api.UserStatsDto
import com.routy.app.logic.api.WalkLogEntryDto
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StatsUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val stats: UserStatsDto? = null,
    val streak: StreakStatsDto? = null,
    val achievements: AchievementsDto? = null,
    val recentWalks: List<WalkLogEntryDto> = emptyList(),
    val leaderboard: List<LeaderboardEntryDto> = emptyList(),
    val currentUserId: Int? = null,
)

class StatsViewModel(
    private val apiClientProvider: ApiClientProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = false)
            val service = apiClientProvider.service
            try {
                val meResponse = service.appStatsMe()
                val boardResponse = runCatching { service.weeklyLeaderboard() }.getOrNull()
                if (meResponse.isSuccessful) {
                    val body = meResponse.body()
                    _uiState.value = StatsUiState(
                        loading = false,
                        stats = body?.stats,
                        streak = body?.streak,
                        achievements = body?.achievements,
                        recentWalks = body?.recentWalks.orEmpty(),
                        leaderboard = boardResponse?.takeIf { it.isSuccessful }?.body()?.leaderboard.orEmpty(),
                        currentUserId = boardResponse?.body()?.userId,
                    )
                } else {
                    _uiState.value = _uiState.value.copy(loading = false, error = true)
                }
            } catch (_: IOException) {
                _uiState.value = _uiState.value.copy(loading = false, error = true)
            }
        }
    }
}
