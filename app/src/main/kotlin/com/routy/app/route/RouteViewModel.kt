package com.routy.app.route

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routy.app.R
import com.routy.app.core.DeepLinkHolder
import com.routy.app.core.BootstrapLoader
import com.routy.app.core.BootstrapResult
import com.routy.app.core.StatsInvalidation
import com.routy.app.core.network.ApiClientProvider
import com.routy.app.logic.cache.CachedBootstrap
import com.routy.app.logic.cache.CachedNetwork
import com.routy.app.core.storage.NetworkCache
import com.routy.app.core.storage.RouteProgressStore
import com.routy.app.map.MapTilePrefetchScheduler
import com.routy.app.logic.api.AchievementsDto
import com.routy.app.logic.api.AdjustRouteRequest
import com.routy.app.logic.api.ApiErrorBody
import com.routy.app.logic.api.FavoriteEntry
import com.routy.app.logic.api.GenerateRouteRequest
import com.routy.app.logic.api.GeoPoint
import com.routy.app.logic.api.NicknameRequest
import com.routy.app.logic.api.NodeDto
import com.routy.app.logic.api.RouteDisplayPayload
import com.routy.app.logic.api.RouteTokenRequest
import com.routy.app.logic.api.RouteStateResponse
import com.routy.app.logic.api.SaveFavoriteRequest
import com.routy.app.logic.api.SegmentDto
import com.routy.app.logic.api.ShareFavoriteRequest
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

enum class RouteMode { SUGGESTING, ACTIVE }
enum class RouteStatus { IDLE, LOADING, ERROR }

data class RouteUiState(
    val loadingInitial: Boolean = true,
    val offlineCached: Boolean = false,
    val nodes: List<NodeDto> = emptyList(),
    val segments: List<SegmentDto> = emptyList(),
    val favorites: List<FavoriteEntry> = emptyList(),

    val startNodeId: Int? = null,
    val isLoop: Boolean = true,
    val destinationNodeId: Int? = null,
    val waypointNodeId: Int? = null,
    val explorerMode: Boolean = false,

    val mode: RouteMode = RouteMode.SUGGESTING,
    val token: String = "",
    val route: RouteDisplayPayload? = null,
    val status: RouteStatus = RouteStatus.IDLE,
    val messageRes: Int? = null,

    val nickname: String = "",
    val nicknameSaving: Boolean = false,

    val savingFavorite: Boolean = false,

    val myLocation: GeoPoint? = null,
    val watchingLocation: Boolean = false,
    val voiceEnabled: Boolean = false,
    val tracking: Boolean = false,
    val keepScreenOn: Boolean = true,
    val completedWaypointIndex: Int = -1,
    val voiceAnnouncedIndex: Int = 0,
    val showControls: Boolean = true,

    val pendingShareUrl: String? = null,
    val pendingShareToken: String? = null,
    val sharedRouteName: String? = null,

    val completionPointsEarned: Int? = null,
    val completionStreakMultiplier: Double? = null,
    val completionCurrentStreak: Int? = null,
    val completionWeeklyPoints: Int? = null,
    val completionNewAchievements: List<String> = emptyList(),
)

class RouteViewModel(
    private val apiClientProvider: ApiClientProvider,
    private val routeProgressStore: RouteProgressStore,
    private val networkCache: NetworkCache,
    private val bootstrapLoader: BootstrapLoader,
    private val mapTilePrefetchScheduler: MapTilePrefetchScheduler,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RouteUiState())
    val uiState: StateFlow<RouteUiState> = _uiState.asStateFlow()

    private val errorJson = Json { ignoreUnknownKeys = true }
    private val initialLoadDone = MutableStateFlow(false)

    init {
        loadInitial()
        viewModelScope.launch {
            initialLoadDone.first { it }
            DeepLinkHolder.shareToken.collect { token ->
                if (token != null) consumeDeepLink()
            }
        }
    }

    private fun routeKey(route: RouteDisplayPayload): String = route.nodeChain.joinToString("-")

    private fun prefetchMapTiles(route: RouteDisplayPayload?) {
        route?.geometry?.let { mapTilePrefetchScheduler.prefetchRoute(it) }
    }

    private fun loadInitial() {
        viewModelScope.launch {
            val cachedBootstrap = networkCache.loadBootstrap()
            val cachedNetwork = networkCache.load()
            if (cachedBootstrap != null) {
                applyNetworkState(
                    cachedBootstrap.nodes,
                    cachedBootstrap.segments,
                    cachedBootstrap.routeState,
                    offlineCached = false,
                )
            } else if (cachedNetwork != null) {
                applyNetworkState(cachedNetwork.nodes, cachedNetwork.segments, null, offlineCached = false)
            }

            when (val result = bootstrapLoader.load()) {
                is BootstrapResult.Fresh -> {
                    applyNetworkState(result.body.nodes, result.body.segments, result.body.routeState, offlineCached = false)
                    restoreProgress(result.body.routeState.activeRoute)
                    consumeDeepLink()
                }
                is BootstrapResult.NotModified -> {
                    applyNetworkState(result.cached.nodes, result.cached.segments, result.cached.routeState, offlineCached = false)
                    restoreProgress(result.cached.routeState.activeRoute)
                    consumeDeepLink()
                }
                is BootstrapResult.CachedOnly -> {
                    applyNetworkState(result.cached.nodes, result.cached.segments, result.cached.routeState, offlineCached = true)
                    restoreProgress(result.cached.routeState.activeRoute)
                    consumeDeepLink()
                }
                BootstrapResult.Unauthorized, BootstrapResult.Failed -> fallbackLoad(cachedBootstrap, cachedNetwork)
            }
            initialLoadDone.value = true
        }
    }

    private suspend fun fallbackLoad(
        cachedBootstrap: CachedBootstrap?,
        cachedNetwork: CachedNetwork?,
    ) {
        val service = apiClientProvider.service
        val networkEtag = cachedBootstrap?.networkVersion ?: cachedNetwork?.etag
        val nodesResponse = runCatching { service.nodes(networkEtag) }.getOrNull()
        val segmentsResponse = runCatching { service.segments(networkEtag) }.getOrNull()
        val stateResponse = runCatching { service.routeState() }.getOrNull()

        val nodes = nodesResponse?.takeIf { it.isSuccessful }?.body()?.nodes
            ?: cachedBootstrap?.nodes ?: cachedNetwork?.nodes.orEmpty()
        val segments = segmentsResponse?.takeIf { it.isSuccessful }?.body()?.segments
            ?: cachedBootstrap?.segments ?: cachedNetwork?.segments.orEmpty()
        val state = stateResponse?.takeIf { it.isSuccessful }?.body() ?: cachedBootstrap?.routeState
        if (nodes.isNotEmpty() && segments.isNotEmpty()) {
            val freshEtag = nodesResponse?.headers()?.get("ETag")?.trim('"')
                ?: segmentsResponse?.headers()?.get("ETag")?.trim('"')
                ?: networkEtag
            networkCache.save(freshEtag ?: "legacy", nodes, segments)
        }
        applyNetworkState(nodes, segments, state, offlineCached = nodesResponse?.isSuccessful != true)
        restoreProgress(state?.activeRoute)
        consumeDeepLink()
    }

    private fun consumeDeepLink() {
        DeepLinkHolder.consumeShareToken()?.let { openShareToken(it) }
    }

    private fun restoreProgress(activeRoute: RouteDisplayPayload?) {
        val route = activeRoute ?: return
        val saved = routeProgressStore.load(routeKey(route)) ?: return
        _uiState.value = _uiState.value.copy(
            completedWaypointIndex = saved.completedIndex,
            voiceAnnouncedIndex = saved.voiceAnnouncedIndex,
        )
    }

    private fun applyNetworkState(
        nodes: List<NodeDto>,
        segments: List<SegmentDto>,
        state: RouteStateResponse?,
        offlineCached: Boolean,
    ) {
        val homeNodeId = nodes.firstOrNull { it.isHome }?.id
        _uiState.value = _uiState.value.copy(
            loadingInitial = false,
            offlineCached = offlineCached,
            nodes = nodes,
            segments = segments,
            favorites = state?.favorites.orEmpty(),
            startNodeId = homeNodeId,
            destinationNodeId = homeNodeId,
            mode = if (state?.activeRoute != null) RouteMode.ACTIVE else RouteMode.SUGGESTING,
            route = state?.activeRoute,
            nickname = state?.nickname ?: "",
        )
        if (state?.activeRoute != null) prefetchMapTiles(state.activeRoute)
    }

    fun setStartNodeId(id: Int) { _uiState.value = _uiState.value.copy(startNodeId = id) }
    fun setIsLoop(loop: Boolean) { _uiState.value = _uiState.value.copy(isLoop = loop) }
    fun setDestinationNodeId(id: Int) { _uiState.value = _uiState.value.copy(destinationNodeId = id) }
    fun setWaypointNodeId(id: Int?) { _uiState.value = _uiState.value.copy(waypointNodeId = id) }
    fun setExplorerMode(enabled: Boolean) { _uiState.value = _uiState.value.copy(explorerMode = enabled) }
    fun setNickname(value: String) { _uiState.value = _uiState.value.copy(nickname = value) }
    fun setMyLocation(point: GeoPoint?) { _uiState.value = _uiState.value.copy(myLocation = point) }
    fun setWatchingLocation(watching: Boolean) {
        _uiState.value = _uiState.value.copy(
            watchingLocation = watching,
            myLocation = if (watching) _uiState.value.myLocation else null,
            tracking = if (watching) _uiState.value.tracking else false,
        )
    }
    fun setVoiceEnabled(enabled: Boolean) { _uiState.value = _uiState.value.copy(voiceEnabled = enabled) }
    fun setTracking(enabled: Boolean) { _uiState.value = _uiState.value.copy(tracking = enabled) }
    fun setKeepScreenOn(enabled: Boolean) { _uiState.value = _uiState.value.copy(keepScreenOn = enabled) }
    fun toggleControls() { _uiState.value = _uiState.value.copy(showControls = !_uiState.value.showControls) }
    fun clearPendingShareUrl() { _uiState.value = _uiState.value.copy(pendingShareUrl = null) }
    fun dismissCompletionStats() {
        _uiState.value = _uiState.value.copy(
            completionPointsEarned = null,
            completionStreakMultiplier = null,
            completionCurrentStreak = null,
            completionWeeklyPoints = null,
            completionNewAchievements = emptyList(),
        )
    }

    fun onWaypointCompleted(index: Int) {
        val route = _uiState.value.route ?: return
        val voiceIndex = _uiState.value.voiceAnnouncedIndex
        routeProgressStore.save(routeKey(route), index, voiceIndex)
        _uiState.value = _uiState.value.copy(completedWaypointIndex = index)
    }

    fun onVoiceCueAnnounced(announcedCount: Int) {
        val route = _uiState.value.route ?: return
        routeProgressStore.save(routeKey(route), _uiState.value.completedWaypointIndex, announcedCount)
        _uiState.value = _uiState.value.copy(voiceAnnouncedIndex = announcedCount)
    }

    fun openShareToken(token: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(status = RouteStatus.LOADING, sharedRouteName = null)
            val response = try {
                apiClientProvider.service.resolveShareToken(token)
            } catch (_: IOException) {
                _uiState.value = _uiState.value.copy(status = RouteStatus.IDLE, messageRes = R.string.common_error)
                return@launch
            }
            if (!response.isSuccessful) {
                _uiState.value = _uiState.value.copy(status = RouteStatus.IDLE, messageRes = R.string.route_share_not_found)
                return@launch
            }
            val body = response.body() ?: return@launch
            if (body.stale || body.display == null) {
                _uiState.value = _uiState.value.copy(status = RouteStatus.IDLE, messageRes = R.string.route_favorite_stale)
                return@launch
            }
            _uiState.value = _uiState.value.copy(
                status = RouteStatus.IDLE,
                sharedRouteName = body.name,
                pendingShareToken = token,
                route = body.display,
                mode = RouteMode.SUGGESTING,
                messageRes = R.string.route_share_preview,
            )
        }
    }

    fun acceptSharedRoute(token: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(status = RouteStatus.LOADING)
            val response = try {
                apiClientProvider.service.acceptShareToken(token)
            } catch (_: IOException) {
                _uiState.value = _uiState.value.copy(status = RouteStatus.IDLE, messageRes = R.string.common_error)
                return@launch
            }
            if (response.isSuccessful) {
                val state = fetchRouteStateWithRetry()
                if (state == null) {
                    _uiState.value = _uiState.value.copy(
                        status = RouteStatus.IDLE,
                        messageRes = R.string.common_error,
                    )
                    return@launch
                }
                routeProgressStore.clear()
                applyNetworkState(_uiState.value.nodes, _uiState.value.segments, state, offlineCached = _uiState.value.offlineCached)
                _uiState.value = _uiState.value.copy(
                    mode = RouteMode.ACTIVE,
                    status = RouteStatus.IDLE,
                    pendingShareToken = null,
                    sharedRouteName = null,
                    completedWaypointIndex = -1,
                    voiceAnnouncedIndex = 0,
                    messageRes = null,
                )
                prefetchMapTiles(state.activeRoute)
            } else {
                val code = parseErrorCode(response.errorBody()?.string())
                _uiState.value = _uiState.value.copy(
                    status = RouteStatus.IDLE,
                    messageRes = if (code == "favorite_stale") R.string.route_favorite_stale else R.string.common_error,
                )
            }
        }
    }

    fun discover() {
        _uiState.value = _uiState.value.copy(explorerMode = true)
        suggest()
    }

    fun suggest(preset: String? = null) {
        val state = _uiState.value
        val startNodeId = state.startNodeId ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(status = RouteStatus.LOADING, messageRes = null)
            val response = try {
                apiClientProvider.service.generateRoute(
                    GenerateRouteRequest(
                        startNodeId = startNodeId,
                        destinationNodeId = if (state.isLoop) startNodeId else (state.destinationNodeId ?: startNodeId),
                        waypointNodeId = state.waypointNodeId,
                        explorerMode = state.explorerMode,
                        preset = preset,
                    ),
                )
            } catch (_: IOException) {
                _uiState.value = _uiState.value.copy(status = RouteStatus.ERROR, route = null, messageRes = R.string.route_no_route_found)
                return@launch
            }
            if (response.isSuccessful) {
                val body = response.body()
                _uiState.value = _uiState.value.copy(status = RouteStatus.IDLE, token = body?.token ?: "", route = body?.route)
            } else {
                _uiState.value = _uiState.value.copy(status = RouteStatus.ERROR, route = null, messageRes = R.string.route_no_route_found)
            }
        }
    }

    fun another() = adjustInternal { apiClientProvider.service.widenRoute(RouteTokenRequest(_uiState.value.token)) }
    fun adjust(direction: String) = adjustInternal { apiClientProvider.service.adjustRoute(AdjustRouteRequest(_uiState.value.token, direction)) }

    private fun adjustInternal(call: suspend () -> retrofit2.Response<com.routy.app.logic.api.GenerateRouteResponse>) {
        if (_uiState.value.route == null) return
        val token = _uiState.value.token
        if (token.isBlank()) {
            _uiState.value = _uiState.value.copy(messageRes = R.string.route_session_expired)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(status = RouteStatus.LOADING)
            val response = try { call() } catch (_: IOException) {
                _uiState.value = _uiState.value.copy(status = RouteStatus.IDLE, messageRes = R.string.route_no_alternative)
                return@launch
            }
            if (response.isSuccessful) {
                val body = response.body()
                _uiState.value = _uiState.value.copy(status = RouteStatus.IDLE, token = body?.token ?: token, route = body?.route, messageRes = null)
            } else {
                _uiState.value = _uiState.value.copy(status = RouteStatus.IDLE, messageRes = R.string.route_no_alternative)
            }
        }
    }

    fun accept() {
        val token = _uiState.value.token
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(status = RouteStatus.LOADING)
            val response = try { apiClientProvider.service.acceptRoute(RouteTokenRequest(token)) } catch (_: IOException) {
                _uiState.value = _uiState.value.copy(status = RouteStatus.IDLE, messageRes = R.string.route_session_expired)
                return@launch
            }
            if (response.isSuccessful) {
                routeProgressStore.clear()
                prefetchMapTiles(_uiState.value.route)
                _uiState.value = _uiState.value.copy(mode = RouteMode.ACTIVE, status = RouteStatus.IDLE, messageRes = null, nickname = "", completedWaypointIndex = -1, voiceAnnouncedIndex = 0)
            } else {
                _uiState.value = _uiState.value.copy(status = RouteStatus.IDLE, messageRes = R.string.route_session_expired)
            }
        }
    }

    fun saveNickname() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(nicknameSaving = true, messageRes = null)
            val response = try {
                apiClientProvider.service.setRouteNickname(NicknameRequest(_uiState.value.nickname))
            } catch (_: IOException) {
                _uiState.value = _uiState.value.copy(nicknameSaving = false, messageRes = R.string.common_error)
                return@launch
            }
            _uiState.value = _uiState.value.copy(
                nicknameSaving = false,
                messageRes = if (response.isSuccessful) R.string.route_nickname_saved else R.string.common_error,
            )
        }
    }

    fun cancel() {
        val token = _uiState.value.token
        if (_uiState.value.route == null) return
        viewModelScope.launch {
            try { apiClientProvider.service.cancelRoute(RouteTokenRequest(token)) } catch (_: IOException) {}
            _uiState.value = _uiState.value.copy(route = null, messageRes = null)
        }
    }

    fun complete() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(status = RouteStatus.LOADING)
            val beforeAchievements = runCatching {
                apiClientProvider.service.appStatsMe()
                    .takeIf { it.isSuccessful }
                    ?.body()
                    ?.achievements
            }.getOrNull()
            val response = try { apiClientProvider.service.completeRoute() } catch (_: IOException) {
                _uiState.value = _uiState.value.copy(status = RouteStatus.IDLE, messageRes = R.string.common_error)
                return@launch
            }
            if (response.isSuccessful) {
                routeProgressStore.clear()
                val body = response.body()
                val afterStats = runCatching {
                    apiClientProvider.service.appStatsMe()
                        .takeIf { it.isSuccessful }
                        ?.body()
                }.getOrNull()
                StatsInvalidation.bump()
                _uiState.value = _uiState.value.copy(
                    route = null,
                    mode = RouteMode.SUGGESTING,
                    status = RouteStatus.IDLE,
                    messageRes = R.string.route_completed_message,
                    nickname = "",
                    watchingLocation = false,
                    tracking = false,
                    myLocation = null,
                    completedWaypointIndex = -1,
                    voiceAnnouncedIndex = 0,
                    completionPointsEarned = body?.pointsEarned,
                    completionStreakMultiplier = body?.streakMultiplier,
                    completionCurrentStreak = body?.currentStreak,
                    completionWeeklyPoints = afterStats?.points?.weeklyPoints,
                    completionNewAchievements = diffNewAchievements(beforeAchievements, afterStats?.achievements),
                )
            } else {
                _uiState.value = _uiState.value.copy(status = RouteStatus.IDLE, messageRes = R.string.common_error)
            }
        }
    }

    fun discardActive() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(status = RouteStatus.LOADING)
            try { apiClientProvider.service.discardRoute() } catch (_: IOException) {}
            routeProgressStore.clear()
            _uiState.value = _uiState.value.copy(
                route = null,
                mode = RouteMode.SUGGESTING,
                status = RouteStatus.IDLE,
                messageRes = null,
                nickname = "",
                watchingLocation = false,
                tracking = false,
                myLocation = null,
                completedWaypointIndex = -1,
                voiceAnnouncedIndex = 0,
            )
        }
    }

    fun saveFavorite() {
        val state = _uiState.value
        val route = state.route ?: return
        val name = state.nickname.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(savingFavorite = true)
            val response = try {
                apiClientProvider.service.saveFavorite(
                    SaveFavoriteRequest(name, route.nodeChain, route.segmentIds, route.lengthM, route.durationMin),
                )
            } catch (_: IOException) {
                _uiState.value = _uiState.value.copy(savingFavorite = false, messageRes = R.string.common_error)
                return@launch
            }
            _uiState.value = _uiState.value.copy(savingFavorite = false)
            if (response.isSuccessful) {
                _uiState.value = _uiState.value.copy(messageRes = R.string.route_favorite_saved)
                refreshFavorites()
            }
        }
    }

    fun takeFavorite(favorite: FavoriteEntry) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(status = RouteStatus.LOADING)
            val response = try { apiClientProvider.service.acceptFavorite(favorite.id) } catch (_: IOException) {
                _uiState.value = _uiState.value.copy(status = RouteStatus.IDLE, messageRes = R.string.common_error)
                return@launch
            }
            if (response.isSuccessful) {
                routeProgressStore.clear()
                prefetchMapTiles(favorite.display)
                _uiState.value = _uiState.value.copy(
                    route = favorite.display,
                    token = "",
                    mode = RouteMode.ACTIVE,
                    status = RouteStatus.IDLE,
                    messageRes = null,
                    nickname = "",
                    completedWaypointIndex = -1,
                    voiceAnnouncedIndex = 0,
                )
            } else {
                val errorCode = parseErrorCode(response.errorBody()?.string())
                _uiState.value = _uiState.value.copy(
                    status = RouteStatus.IDLE,
                    messageRes = if (errorCode == "favorite_stale") R.string.route_favorite_stale else R.string.common_error,
                )
            }
        }
    }

    fun deleteFavorite(id: Int) {
        viewModelScope.launch {
            try {
                apiClientProvider.service.deleteFavorite(id)
            } catch (_: IOException) {
                _uiState.value = _uiState.value.copy(messageRes = R.string.common_error)
                return@launch
            }
            refreshFavorites()
        }
    }

    fun dismissSharedRoute() {
        _uiState.value = _uiState.value.copy(
            route = null,
            token = "",
            pendingShareToken = null,
            sharedRouteName = null,
            mode = RouteMode.SUGGESTING,
            status = RouteStatus.IDLE,
            messageRes = null,
        )
    }

    fun copyFavoriteShareLink(favorite: FavoriteEntry) {
        val token = favorite.shareToken ?: return
        _uiState.value = _uiState.value.copy(
            pendingShareUrl = "routy://share/$token",
            messageRes = R.string.route_favorite_share_copied,
        )
    }

    fun toggleShare(favorite: FavoriteEntry) {
        viewModelScope.launch {
            val response = try {
                apiClientProvider.service.shareFavorite(favorite.id, ShareFavoriteRequest(enable = favorite.shareToken == null))
            } catch (_: IOException) {
                _uiState.value = _uiState.value.copy(messageRes = R.string.common_error)
                return@launch
            }
            if (!response.isSuccessful) {
                _uiState.value = _uiState.value.copy(messageRes = R.string.common_error)
                return@launch
            }
            val shareToken = response.body()?.shareToken
            _uiState.value = _uiState.value.copy(
                pendingShareUrl = shareToken?.let { "routy://share/$it" },
                messageRes = if (shareToken != null) R.string.route_favorite_share_copied else R.string.route_favorite_unshared,
            )
            refreshFavorites()
        }
    }

    private suspend fun refreshFavorites() {
        val response = runCatching { apiClientProvider.service.routeState() }.getOrNull()
        val favorites = response?.takeIf { it.isSuccessful }?.body()?.favorites ?: return
        _uiState.value = _uiState.value.copy(favorites = favorites)
    }

    private suspend fun fetchRouteStateWithRetry(): RouteStateResponse? {
        repeat(2) { attempt ->
            val response = runCatching { apiClientProvider.service.routeState() }.getOrNull()
            if (response?.isSuccessful == true) return response.body()
            if (attempt == 0) delay(400)
        }
        return null
    }

    private fun parseErrorCode(errorBodyJson: String?): String? {
        if (errorBodyJson == null) return null
        return try { errorJson.decodeFromString(ApiErrorBody.serializer(), errorBodyJson).error } catch (_: Exception) { null }
    }

    private fun diffNewAchievements(before: AchievementsDto?, after: AchievementsDto?): List<String> {
        if (before == null || after == null) return emptyList()
        val labels = mutableListOf<String>()
        for (afterItem in after.scalable) {
            val beforeItem = before.scalable.find { it.category == afterItem.category } ?: continue
            if (afterItem.tierIndex > beforeItem.tierIndex && afterItem.tierLabel != null) {
                labels.add("${afterItem.categoryLabel}: ${afterItem.tierLabel}")
            }
        }
        for (afterItem in after.special) {
            val beforeItem = before.special.find { it.id == afterItem.id } ?: continue
            if (afterItem.earned && !beforeItem.earned) {
                labels.add(afterItem.label)
            }
        }
        return labels
    }
}
