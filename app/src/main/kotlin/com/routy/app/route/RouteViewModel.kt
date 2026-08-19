package com.routy.app.route

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routy.app.R
import com.routy.app.core.network.ApiClientProvider
import com.routy.app.logic.api.AdjustRouteRequest
import com.routy.app.logic.api.ApiErrorBody
import com.routy.app.logic.api.FavoriteEntry
import com.routy.app.logic.api.GenerateRouteRequest
import com.routy.app.logic.api.GeoPoint
import com.routy.app.logic.api.NicknameRequest
import com.routy.app.logic.api.NodeDto
import com.routy.app.logic.api.RouteDisplayPayload
import com.routy.app.logic.api.RouteTokenRequest
import com.routy.app.logic.api.SaveFavoriteRequest
import com.routy.app.logic.api.SegmentDto
import com.routy.app.logic.api.ShareFavoriteRequest
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

enum class RouteMode { SUGGESTING, ACTIVE }
enum class RouteStatus { IDLE, LOADING, ERROR }

data class RouteUiState(
    val loadingInitial: Boolean = true,
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

    val favoriteNameInput: String = "",
    val savingFavorite: Boolean = false,

    val myLocation: GeoPoint? = null,
    val watchingLocation: Boolean = false,

    /** One-shot: the composable copies this to the clipboard and calls [clearPendingShareUrl]. */
    val pendingShareUrl: String? = null,
)

/**
 * Ports RouteGenerator.tsx's suggesting/active state machine (see the plan's "What gets ported
 * vs. reused" section) — every handler here is a straight translation of that component's
 * eponymous handler, calling the exact same REST endpoints instead of `fetch`. Voice guidance
 * (the web component's voiceEnabled/announcedStationIndex bits) is deliberately not ported yet —
 * that's M5, wired in once TextToSpeech/AudioManager focus is in place.
 */
class RouteViewModel(
    private val apiClientProvider: ApiClientProvider,
    private val baseUrl: String,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RouteUiState())
    val uiState: StateFlow<RouteUiState> = _uiState.asStateFlow()

    private val errorJson = Json { ignoreUnknownKeys = true }

    init {
        loadInitial()
    }

    private fun loadInitial() {
        viewModelScope.launch {
            val service = apiClientProvider.service
            val nodesResponse = runCatching { service.nodes() }.getOrNull()
            val segmentsResponse = runCatching { service.segments() }.getOrNull()
            val stateResponse = runCatching { service.routeState() }.getOrNull()

            val nodes = nodesResponse?.takeIf { it.isSuccessful }?.body()?.nodes.orEmpty()
            val segments = segmentsResponse?.takeIf { it.isSuccessful }?.body()?.segments.orEmpty()
            val state = stateResponse?.takeIf { it.isSuccessful }?.body()
            val homeNodeId = nodes.firstOrNull { it.isHome }?.id

            _uiState.value = _uiState.value.copy(
                loadingInitial = false,
                nodes = nodes,
                segments = segments,
                favorites = state?.favorites.orEmpty(),
                startNodeId = homeNodeId,
                destinationNodeId = homeNodeId,
                mode = if (state?.activeRoute != null) RouteMode.ACTIVE else RouteMode.SUGGESTING,
                route = state?.activeRoute,
                nickname = state?.nickname ?: "",
            )
        }
    }

    fun setStartNodeId(id: Int) {
        _uiState.value = _uiState.value.copy(startNodeId = id)
    }

    fun setIsLoop(loop: Boolean) {
        _uiState.value = _uiState.value.copy(isLoop = loop)
    }

    fun setDestinationNodeId(id: Int) {
        _uiState.value = _uiState.value.copy(destinationNodeId = id)
    }

    fun setWaypointNodeId(id: Int?) {
        _uiState.value = _uiState.value.copy(waypointNodeId = id)
    }

    fun setExplorerMode(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(explorerMode = enabled)
    }

    fun setNickname(value: String) {
        _uiState.value = _uiState.value.copy(nickname = value)
    }

    fun setFavoriteNameInput(value: String) {
        _uiState.value = _uiState.value.copy(favoriteNameInput = value)
    }

    fun setMyLocation(point: GeoPoint?) {
        _uiState.value = _uiState.value.copy(myLocation = point)
    }

    fun setWatchingLocation(watching: Boolean) {
        _uiState.value = _uiState.value.copy(watchingLocation = watching, myLocation = if (watching) _uiState.value.myLocation else null)
    }

    fun clearPendingShareUrl() {
        _uiState.value = _uiState.value.copy(pendingShareUrl = null)
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

    fun another() {
        val token = _uiState.value.token
        if (_uiState.value.route == null) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(status = RouteStatus.LOADING)
            val response = try {
                apiClientProvider.service.widenRoute(RouteTokenRequest(token))
            } catch (_: IOException) {
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

    fun adjust(direction: String) {
        val token = _uiState.value.token
        if (_uiState.value.route == null) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(status = RouteStatus.LOADING)
            val response = try {
                apiClientProvider.service.adjustRoute(AdjustRouteRequest(token, direction))
            } catch (_: IOException) {
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
            val response = try {
                apiClientProvider.service.acceptRoute(RouteTokenRequest(token))
            } catch (_: IOException) {
                _uiState.value = _uiState.value.copy(status = RouteStatus.IDLE, messageRes = R.string.route_session_expired)
                return@launch
            }
            if (response.isSuccessful) {
                _uiState.value = _uiState.value.copy(mode = RouteMode.ACTIVE, status = RouteStatus.IDLE, messageRes = null, nickname = "")
            } else {
                _uiState.value = _uiState.value.copy(status = RouteStatus.IDLE, messageRes = R.string.route_session_expired)
            }
        }
    }

    fun saveNickname() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(nicknameSaving = true)
            try {
                apiClientProvider.service.setRouteNickname(NicknameRequest(_uiState.value.nickname))
            } catch (_: IOException) {
                // Best-effort, like the web client — a failed nickname save just leaves the old one.
            }
            _uiState.value = _uiState.value.copy(nicknameSaving = false)
        }
    }

    fun cancel() {
        val token = _uiState.value.token
        if (_uiState.value.route == null) return
        viewModelScope.launch {
            try {
                apiClientProvider.service.cancelRoute(RouteTokenRequest(token))
            } catch (_: IOException) {
                // Best-effort — the suggestion session just expires server-side on its own.
            }
            _uiState.value = _uiState.value.copy(route = null, messageRes = null)
        }
    }

    fun complete() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(status = RouteStatus.LOADING)
            val response = try {
                apiClientProvider.service.completeRoute()
            } catch (_: IOException) {
                _uiState.value = _uiState.value.copy(status = RouteStatus.IDLE, messageRes = R.string.common_error)
                return@launch
            }
            if (response.isSuccessful) {
                _uiState.value = _uiState.value.copy(
                    route = null,
                    mode = RouteMode.SUGGESTING,
                    status = RouteStatus.IDLE,
                    messageRes = R.string.route_completed_message,
                    nickname = "",
                    watchingLocation = false,
                    myLocation = null,
                )
            } else {
                _uiState.value = _uiState.value.copy(status = RouteStatus.IDLE, messageRes = R.string.common_error)
            }
        }
    }

    fun discardActive() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(status = RouteStatus.LOADING)
            try {
                apiClientProvider.service.discardRoute()
            } catch (_: IOException) {
                // Best-effort — falls through to clearing local state regardless.
            }
            _uiState.value = _uiState.value.copy(
                route = null,
                mode = RouteMode.SUGGESTING,
                status = RouteStatus.IDLE,
                messageRes = null,
                nickname = "",
                watchingLocation = false,
                myLocation = null,
            )
        }
    }

    fun saveFavorite() {
        val state = _uiState.value
        val route = state.route ?: return
        val name = state.favoriteNameInput.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(savingFavorite = true)
            val response = try {
                apiClientProvider.service.saveFavorite(
                    SaveFavoriteRequest(
                        name = name,
                        nodeChain = route.nodeChain,
                        segmentIds = route.segmentIds,
                        lengthM = route.lengthM,
                        durationMin = route.durationMin,
                    ),
                )
            } catch (_: IOException) {
                _uiState.value = _uiState.value.copy(savingFavorite = false)
                return@launch
            }
            _uiState.value = _uiState.value.copy(savingFavorite = false)
            if (response.isSuccessful) {
                _uiState.value = _uiState.value.copy(favoriteNameInput = "", messageRes = R.string.route_favorite_saved)
                refreshFavorites()
            }
        }
    }

    fun takeFavorite(favorite: FavoriteEntry) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(status = RouteStatus.LOADING)
            val response = try {
                apiClientProvider.service.acceptFavorite(favorite.id)
            } catch (_: IOException) {
                _uiState.value = _uiState.value.copy(status = RouteStatus.IDLE, messageRes = R.string.common_error)
                return@launch
            }
            if (response.isSuccessful) {
                _uiState.value = _uiState.value.copy(
                    route = favorite.display,
                    token = "",
                    mode = RouteMode.ACTIVE,
                    status = RouteStatus.IDLE,
                    messageRes = null,
                    nickname = "",
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
                return@launch
            }
            refreshFavorites()
        }
    }

    fun toggleShare(favorite: FavoriteEntry) {
        viewModelScope.launch {
            val response = try {
                apiClientProvider.service.shareFavorite(favorite.id, ShareFavoriteRequest(enable = favorite.shareToken == null))
            } catch (_: IOException) {
                return@launch
            }
            if (!response.isSuccessful) return@launch
            val shareToken = response.body()?.shareToken
            _uiState.value = _uiState.value.copy(pendingShareUrl = shareToken?.let { "$baseUrl/share/$it" })
            refreshFavorites()
        }
    }

    private suspend fun refreshFavorites() {
        val response = runCatching { apiClientProvider.service.routeState() }.getOrNull()
        val favorites = response?.takeIf { it.isSuccessful }?.body()?.favorites ?: return
        _uiState.value = _uiState.value.copy(favorites = favorites)
    }

    private fun parseErrorCode(errorBodyJson: String?): String? {
        if (errorBodyJson == null) return null
        return try {
            errorJson.decodeFromString(ApiErrorBody.serializer(), errorBodyJson).error
        } catch (_: Exception) {
            null
        }
    }
}
