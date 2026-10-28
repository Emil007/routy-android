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
import com.routy.app.core.storage.RouteWalkTrackStore
import com.routy.app.logic.api.AdjustRouteRequest
import com.routy.app.logic.api.FreeRoamStartRequest
import com.routy.app.logic.api.FreeRoamCheckinRequest
import com.routy.app.logic.api.FreeRoamCompleteRequest
import com.routy.app.logic.api.FreeRoamReachedNode
import com.routy.app.logic.api.FreeRoamNeighbour
import com.routy.app.map.MapTilePrefetchScheduler
import com.routy.app.logic.api.AchievementsDto
import com.routy.app.logic.api.CompleteRouteRequest
import com.routy.app.logic.api.ApiErrorBody
import com.routy.app.logic.api.FavoriteEntry
import com.routy.app.logic.api.GenerateRouteRequest
import com.routy.app.logic.api.GuideStartRequest
import com.routy.app.logic.api.RepositionNodeRequest
import com.routy.app.logic.api.GameSummaryDto
import com.routy.app.logic.api.GeoPoint
import com.routy.app.logic.api.NicknameRequest
import com.routy.app.logic.api.NodeDto
import com.routy.app.logic.api.NodeMoveRequest
import com.routy.app.logic.api.NodeRenameRequest
import com.routy.app.logic.api.PointPreviewBreakdown
import com.routy.app.logic.api.RouteDisplayPayload
import com.routy.app.logic.api.RateWalkRequest
import com.routy.app.logic.api.RouteTokenRequest
import com.routy.app.logic.api.RouteStateResponse
import com.routy.app.logic.api.SaveFavoriteRequest
import com.routy.app.logic.api.SegmentDto
import com.routy.app.logic.api.goldenHitsOnRoute
import com.routy.app.logic.api.ShareFavoriteRequest
import com.routy.app.logic.geo.LatLng
import com.routy.app.logic.geo.haversineMeters
import com.routy.app.logic.graph.disconnectedCanonicalSegmentIds
import com.routy.app.logic.route.RetagLocationBuffer
import com.routy.app.logic.route.ROUTE_TRACK_GEOMETRY_ACCURACY_MAX_M
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

/** Map compass cycle: north + free rotate → heading-up → north locked. */
enum class MapCompassMode { NORTH_FREE, HEADING_UP, NORTH_LOCKED }

data class RouteUiState(
    val loadingInitial: Boolean = true,
    val offlineCached: Boolean = false,
    val nodes: List<NodeDto> = emptyList(),
    val segments: List<SegmentDto> = emptyList(),
    val favorites: List<FavoriteEntry> = emptyList(),
    val homeNodeId: Int? = null,

    val startNodeId: Int? = null,
    val isLoop: Boolean = true,
    val destinationNodeId: Int? = null,
    val mustVisitNodeIds: List<Int> = emptyList(),
    val pendingDestinationReplace: Int? = null,
    val requiredSegmentIds: List<Int> = emptyList(),
    val excludedSegmentIds: List<Int> = emptyList(),
    val selectedNodeId: Int? = null,
    val selectedSegmentId: Int? = null,
    val forceGolden: Boolean = false,
    val explorerMode: Boolean = false,

    val mode: RouteMode = RouteMode.SUGGESTING,
    val token: String = "",
    val route: RouteDisplayPayload? = null,
    val status: RouteStatus = RouteStatus.IDLE,
    val messageRes: Int? = null,
    val messageArgs: List<Any> = emptyList(),

    val nickname: String = "",
    val nicknameSaving: Boolean = false,

    val savingFavorite: Boolean = false,

    val myLocation: GeoPoint? = null,
    val locationBearing: Float? = null,
    val locationSpeed: Float? = null,
    val followEnabled: Boolean = false,
    val voiceEnabled: Boolean = false,
    val trackEnabled: Boolean = false,
    /** M4: epoch-ms, gesetzt beim Go (Tracking-Start) — Grundlage für durationMin
     *  bei Complete statt der geplanten Plan-Dauer. */
    val walkStartedAtMs: Long? = null,
    val trackedGeometry: List<GeoPoint> = emptyList(),
    val compassMode: MapCompassMode = MapCompassMode.NORTH_FREE,
    val keepScreenOn: Boolean = true,
    val completedWaypointIndex: Int = -1,
    val voiceAnnouncedIndex: Int = 0,
    val showControls: Boolean = true,

    /** Active-walk retag (spec D7): long-press nearby station → name-parts editor. */
    val retagNodeId: Int? = null,
    val retagLat: Double? = null,
    val retagLng: Double? = null,
    val retagPart1: String = "",
    val retagPart2: String = "",
    val retagOfferMove: Boolean = false,
    val retagMoveLat: Double? = null,
    val retagMoveLng: Double? = null,
    val retagSaving: Boolean = false,

    val pendingShareUrl: String? = null,
    val pendingShareToken: String? = null,
    val sharedRouteName: String? = null,

    val completionPointsEarned: Int? = null,
    val completionWalkId: Int? = null,
    val completionRouteSnapshot: RouteDisplayPayload? = null,
    val completionFavoriteName: String = "",
    val completionLengthRating: Int? = null,
    val completionRatingSaving: Boolean = false,
    val completionRatingSaved: Boolean = false,
    val completionStreakMultiplier: Double? = null,
    val completionCurrentStreak: Int? = null,
    val completionWeeklyPoints: Int? = null,
    val completionNewAchievements: List<String> = emptyList(),
    val completionPointBreakdown: PointPreviewBreakdown? = null,
    val completionGoldenHits: Int = 0,
    val completionCelebrationTier: String = "normal",

    val totalPoints: Int = 0,
    val weeklyPoints: Int = 0,
    val streakMultiplier: Double = 1.0,
    val todayGoldenSegmentIds: Set<Int> = emptySet(),
    val disconnectedSegmentIds: Set<Int> = emptySet(),
    val pointPreview: PointPreviewBreakdown? = null,
    val goldenHitIds: Set<Int> = emptySet(),
    /** null = unknown; true = network suggest min/max; false = personal taste. */
    val usingNetworkFallback: Boolean? = null,

    /** Routeless node guide — no fixed polyline; stations from ordered nodes. */
    val guideMode: Boolean = false,
    val pointsMultiplier: Double? = null,
    val completionGuideMode: Boolean = false,

    // --- Free roam (0.47) ---
    val freeRoamActive: Boolean = false,
    val freeRoamToken: String = "",
    val freeRoamStartNodeId: Int? = null,
    val freeRoamReachedCount: Int = 0,
    val freeRoamAnnounce: String? = null,

    val repositionCandidates: List<Int> = emptyList(),
    val repositionAccuracyM: Double = 35.0,
)

class RouteViewModel(
    private val apiClientProvider: ApiClientProvider,
    private val routeProgressStore: RouteProgressStore,
    private val routeWalkTrackStore: RouteWalkTrackStore,
    private val networkCache: NetworkCache,
    private val bootstrapLoader: BootstrapLoader,
    private val mapTilePrefetchScheduler: MapTilePrefetchScheduler,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RouteUiState())
    val uiState: StateFlow<RouteUiState> = _uiState.asStateFlow()

    private val errorJson = Json { ignoreUnknownKeys = true }
    private val initialLoadDone = MutableStateFlow(false)
    private val retagLocationBuffer = RetagLocationBuffer()

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
                    cachedBootstrap.game,
                    cachedBootstrap.todayGoldenSegmentIds,
                    offlineCached = false,
                    homeNodeId = cachedBootstrap.user.homeNodeId,
                )
            } else if (cachedNetwork != null) {
                applyNetworkState(cachedNetwork.nodes, cachedNetwork.segments, null, offlineCached = false)
            }

            when (val result = bootstrapLoader.load()) {
                is BootstrapResult.Fresh -> {
                    applyNetworkState(
                        result.body.nodes,
                        result.body.segments,
                        result.body.routeState,
                        result.body.game,
                        result.body.todayGoldenSegmentIds,
                        offlineCached = false,
                        homeNodeId = result.body.user.homeNodeId,
                    )
                    restoreProgress(result.body.routeState.activeRoute)
                    consumeDeepLink()
                }
                is BootstrapResult.NotModified -> {
                    applyNetworkState(
                        result.cached.nodes,
                        result.cached.segments,
                        result.cached.routeState,
                        result.cached.game,
                        result.cached.todayGoldenSegmentIds,
                        offlineCached = false,
                        homeNodeId = result.cached.user.homeNodeId,
                    )
                    restoreProgress(result.cached.routeState.activeRoute)
                    consumeDeepLink()
                }
                is BootstrapResult.CachedOnly -> {
                    applyNetworkState(
                        result.cached.nodes,
                        result.cached.segments,
                        result.cached.routeState,
                        result.cached.game,
                        result.cached.todayGoldenSegmentIds,
                        offlineCached = true,
                        homeNodeId = result.cached.user.homeNodeId,
                    )
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
        applyNetworkState(
            nodes,
            segments,
            state,
            GameSummaryDto(_uiState.value.totalPoints, _uiState.value.weeklyPoints, _uiState.value.streakMultiplier),
            _uiState.value.todayGoldenSegmentIds.toList(),
            offlineCached = nodesResponse?.isSuccessful != true,
            homeNodeId = cachedBootstrap?.user?.homeNodeId,
        )
        restoreProgress(state?.activeRoute)
        consumeDeepLink()
    }

    private fun consumeDeepLink() {
        DeepLinkHolder.consumeShareToken()?.let { openShareToken(it) }
    }

    private fun restoreProgress(activeRoute: RouteDisplayPayload?) {
        val route = activeRoute ?: return
        val key = routeKey(route)
        val saved = routeProgressStore.load(key) ?: return
        val savedTrack = routeWalkTrackStore.load(key)
        _uiState.value = _uiState.value.copy(
            completedWaypointIndex = saved.completedIndex,
            voiceAnnouncedIndex = saved.voiceAnnouncedIndex,
            trackedGeometry = savedTrack?.points
                ?.filter { point ->
                    val accuracy = point.accuracy
                    accuracy == null || accuracy <= ROUTE_TRACK_GEOMETRY_ACCURACY_MAX_M
                }
                ?.map { GeoPoint(it.lat, it.lng) }
                .orEmpty(),
        )
    }

    private fun applyNetworkState(
        nodes: List<NodeDto>,
        segments: List<SegmentDto>,
        state: RouteStateResponse?,
        game: GameSummaryDto = GameSummaryDto(),
        todayGoldenSegmentIds: List<Int> = emptyList(),
        offlineCached: Boolean,
        homeNodeId: Int? = null,
    ) {
        val resolvedHome = homeNodeId ?: nodes.firstOrNull { it.isHome }?.id
        val disconnected = disconnectedCanonicalSegmentIds(segments, resolvedHome)
        _uiState.value = _uiState.value.copy(
            loadingInitial = false,
            offlineCached = offlineCached,
            nodes = nodes,
            segments = segments,
            favorites = state?.favorites.orEmpty(),
            homeNodeId = resolvedHome,
            startNodeId = _uiState.value.startNodeId ?: resolvedHome,
            destinationNodeId = _uiState.value.destinationNodeId ?: resolvedHome,
            mode = if (state?.activeRoute != null) RouteMode.ACTIVE else RouteMode.SUGGESTING,
            route = state?.activeRoute,
            guideMode = state?.walkMode == "guide",
            nickname = state?.nickname ?: "",
            // M3: Eine serverseitig aktive Route (Bootstrap/Favorit/Share) landet auf
            // der Zwischenseite — GPS-Folgen/Tracking erst beim Go (accept).
            followEnabled = false,
            trackEnabled = false,
            totalPoints = game.totalPoints,
            weeklyPoints = game.weeklyPoints,
            streakMultiplier = game.streakMultiplier,
            todayGoldenSegmentIds = todayGoldenSegmentIds.toSet(),
            disconnectedSegmentIds = disconnected,
            goldenHitIds = state?.activeRoute?.let {
                goldenHitsOnRoute(it.segmentIds, todayGoldenSegmentIds.toSet(), segments)
            } ?: emptySet(),
        )
        if (state?.activeRoute != null) prefetchMapTiles(state.activeRoute)
    }

    fun setStartNodeId(id: Int) {
        _uiState.value = _uiState.value.copy(
            startNodeId = id,
            destinationNodeId = _uiState.value.destinationNodeId,
            isLoop = _uiState.value.destinationNodeId == null,
        )
    }
    fun setIsLoop(loop: Boolean) {
        val state = _uiState.value
        _uiState.value = state.copy(
            isLoop = loop,
            destinationNodeId = if (loop) null else state.destinationNodeId,
        )
    }
    fun setDestinationNodeId(id: Int?) { _uiState.value = _uiState.value.copy(destinationNodeId = id, isLoop = id == null) }
    fun setForceGolden(value: Boolean) { _uiState.value = _uiState.value.copy(forceGolden = value) }
    fun setExplorerMode(enabled: Boolean) { _uiState.value = _uiState.value.copy(explorerMode = enabled) }
    fun setNickname(value: String) { _uiState.value = _uiState.value.copy(nickname = value) }
    fun setCompletionFavoriteName(value: String) { _uiState.value = _uiState.value.copy(completionFavoriteName = value) }

    fun onPlanningMapClick(lat: Double, lng: Double) {
        val state = _uiState.value
        if (state.route != null || state.mode != RouteMode.SUGGESTING) return
        val point = com.routy.app.logic.geo.LatLng(lat, lng)
        val nearestNode = state.nodes
            .map { it to com.routy.app.logic.geo.haversineMeters(point, com.routy.app.logic.geo.LatLng(it.lat, it.lng)) }
            .minByOrNull { it.second }
            ?.takeIf { it.second <= PLANNING_NODE_TAP_RADIUS_M }
            ?.first
        if (nearestNode != null) {
            _uiState.value = state.copy(selectedNodeId = nearestNode.id, selectedSegmentId = null)
            return
        }
        val segmentHit = com.routy.app.logic.geo.findSegmentAtTap(state.segments, point)
        if (segmentHit != null) {
            _uiState.value = state.copy(selectedSegmentId = segmentHit.first.id, selectedNodeId = null)
            return
        }
        _uiState.value = state.copy(selectedNodeId = null, selectedSegmentId = null)
    }

    private fun clearNodeRole(nodeId: Int) {
        val state = _uiState.value
        val home = state.homeNodeId
        val dest = if (state.destinationNodeId == nodeId) null else state.destinationNodeId
        _uiState.value = state.copy(
            startNodeId = if (state.startNodeId == nodeId) home else state.startNodeId,
            destinationNodeId = dest,
            mustVisitNodeIds = state.mustVisitNodeIds.filter { it != nodeId },
            isLoop = dest == null,
        )
    }

    fun setNodeAsStart(nodeId: Int) {
        clearNodeRole(nodeId)
        val state = _uiState.value
        val dest = state.destinationNodeId
        _uiState.value = state.copy(
            startNodeId = nodeId,
            destinationNodeId = dest,
            isLoop = dest == null,
            selectedNodeId = null,
        )
    }

    fun setNodeAsEnd(nodeId: Int) {
        clearNodeRole(nodeId)
        val state = _uiState.value
        val existing = state.destinationNodeId
        if (existing != null && existing != nodeId) {
            // A destination is already set — ask whether to switch, only for this calculation.
            _uiState.value = _uiState.value.copy(pendingDestinationReplace = nodeId, selectedNodeId = null)
            return
        }
        _uiState.value = _uiState.value.copy(destinationNodeId = nodeId, isLoop = false, selectedNodeId = null)
    }

    fun confirmReplaceDestination() {
        val nodeId = _uiState.value.pendingDestinationReplace ?: return
        _uiState.value = _uiState.value.copy(
            destinationNodeId = nodeId,
            isLoop = false,
            pendingDestinationReplace = null,
            selectedNodeId = null,
        )
    }

    fun dismissReplaceDestination() {
        _uiState.value = _uiState.value.copy(pendingDestinationReplace = null, selectedNodeId = null)
    }

    /** Clears every planning marker (destination, must-visit, required/excluded segments), keeping start/home. */
    fun resetPlanning() {
        _uiState.value = _uiState.value.copy(
            isLoop = true,
            destinationNodeId = null,
            mustVisitNodeIds = emptyList(),
            requiredSegmentIds = emptyList(),
            excludedSegmentIds = emptyList(),
            selectedNodeId = null,
            selectedSegmentId = null,
            pendingDestinationReplace = null,
        )
    }

    fun toggleMustVisit(nodeId: Int) {
        val state = _uiState.value
        if (state.mustVisitNodeIds.contains(nodeId)) {
            _uiState.value = state.copy(
                mustVisitNodeIds = state.mustVisitNodeIds.filter { it != nodeId },
                selectedNodeId = null,
            )
        } else {
            if (state.destinationNodeId == nodeId) {
                // A node can't be both destination and must-visit — destination wins.
                _uiState.value = state.copy(
                    destinationNodeId = null,
                    isLoop = true,
                    mustVisitNodeIds = state.mustVisitNodeIds + nodeId,
                    selectedNodeId = null,
                )
                return
            }
            _uiState.value = state.copy(
                startNodeId = if (state.startNodeId == nodeId) state.homeNodeId else state.startNodeId,
                mustVisitNodeIds = state.mustVisitNodeIds + nodeId,
                selectedNodeId = null,
            )
        }
    }

    fun clearSelectedNodeRole() {
        val nodeId = _uiState.value.selectedNodeId ?: return
        clearNodeRole(nodeId)
        _uiState.value = _uiState.value.copy(selectedNodeId = null)
    }

    fun setSegmentRequired(segmentId: Int) {
        val state = _uiState.value
        _uiState.value = state.copy(
            excludedSegmentIds = state.excludedSegmentIds.filter { it != segmentId },
            requiredSegmentIds = if (state.requiredSegmentIds.contains(segmentId)) state.requiredSegmentIds else state.requiredSegmentIds + segmentId,
            selectedSegmentId = null,
        )
    }

    fun setSegmentExcluded(segmentId: Int) {
        val state = _uiState.value
        _uiState.value = state.copy(
            requiredSegmentIds = state.requiredSegmentIds.filter { it != segmentId },
            excludedSegmentIds = if (state.excludedSegmentIds.contains(segmentId)) state.excludedSegmentIds else state.excludedSegmentIds + segmentId,
            selectedSegmentId = null,
        )
    }

    fun clearSegmentConstraint(segmentId: Int) {
        val state = _uiState.value
        _uiState.value = state.copy(
            requiredSegmentIds = state.requiredSegmentIds.filter { it != segmentId },
            excludedSegmentIds = state.excludedSegmentIds.filter { it != segmentId },
            selectedSegmentId = null,
        )
    }

    fun clearPlanningSelection() {
        _uiState.value = _uiState.value.copy(selectedNodeId = null, selectedSegmentId = null)
    }
    fun setMyLocation(point: GeoPoint?, accuracyM: Float? = null) {
        _uiState.value = _uiState.value.copy(
            myLocation = point,
            repositionAccuracyM = accuracyM?.toDouble()?.coerceAtLeast(1.0) ?: _uiState.value.repositionAccuracyM,
        )
        if (_uiState.value.freeRoamActive && point != null) {
            maybeCheckinFreeRoam(point)
        }
    }
    fun addRetagLocationSample(lat: Double, lng: Double, accuracyM: Float?) {
        retagLocationBuffer.addSample(lat, lng, accuracyM)
    }
    fun setLocationMotion(bearing: Float?, speed: Float?) {
        _uiState.value = _uiState.value.copy(locationBearing = bearing, locationSpeed = speed)
    }

    // --- Free roam (0.47): start anywhere, track, announce reachable junctions ---

    /** Distance a free-roam checkin must move before another API call fires (mirrors server 30m reach, throttled). */
    private var freeRoamLastCheckinLat: Double? = null
    private var freeRoamLastCheckinLng: Double? = null
    private var freeRoamCheckinInFlight = false

    /**
     * Free-roam keeps its OWN track, independent of the route-tracking service:
     * free-roam is explicitly not a guided route, so TP still has a real walked
     * polyline at finish. Barely-moved fixes are skipped to keep it lean.
     */
    private val freeRoamTrack = mutableListOf<GeoPoint>()
    private var freeRoamStartedAtMs: Long? = null

    fun startFreeRoam() {
        val loc = _uiState.value.myLocation
        if (loc == null) {
            _uiState.value = _uiState.value.copy(messageRes = R.string.free_roam_no_position, messageArgs = emptyList())
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(status = RouteStatus.LOADING, messageRes = null, messageArgs = emptyList())
            val res = try {
                apiClientProvider.service.startFreeRoam(FreeRoamStartRequest(lat = loc.lat, lng = loc.lng))
            } catch (_: IOException) {
                _uiState.value = _uiState.value.copy(status = RouteStatus.IDLE, messageRes = R.string.common_error, messageArgs = emptyList())
                return@launch
            }
            if (res.isSuccessful && res.body() != null) {
                val body = res.body()!!
                freeRoamLastCheckinLat = null
                freeRoamLastCheckinLng = null
                freeRoamTrack.clear()
                freeRoamTrack.add(loc)
                freeRoamStartedAtMs = System.currentTimeMillis()
                _uiState.value = _uiState.value.copy(
                    freeRoamActive = true,
                    freeRoamToken = body.token,
                    freeRoamStartNodeId = body.startNodeId,
                    freeRoamReachedCount = if (body.startReachable.isNotEmpty()) 1 else 0,
                    // 0.57: Start-Ansage kommt jetzt lokalisiert vom Server
                    // („Angekommen bei X, mögliche Wege zu… Richtung…“) — einmal, nicht doppelt.
                    freeRoamAnnounce = body.announcement,
                    status = RouteStatus.IDLE,
                    messageRes = null,
                    messageArgs = emptyList(),
                    // Frei gehen zeichnet denselben Foreground-Track auf wie Routerfolgen.
                    trackEnabled = true,
                )
            } else {
                _uiState.value = _uiState.value.copy(status = RouteStatus.IDLE, messageRes = R.string.common_error, messageArgs = emptyList())
            }
        }
    }

    fun cancelFreeRoam() {
        _uiState.value = _uiState.value.copy(
            freeRoamActive = false,
            freeRoamToken = "",
            freeRoamStartNodeId = null,
            freeRoamReachedCount = 0,
            freeRoamAnnounce = null,
            trackEnabled = false,
        )
        freeRoamLastCheckinLat = null
        freeRoamLastCheckinLng = null
        freeRoamTrack.clear()
        freeRoamStartedAtMs = null
    }

    fun finishFreeRoam() {
        val token = _uiState.value.freeRoamToken
        if (token.isEmpty()) {
            cancelFreeRoam()
            return
        }
        viewModelScope.launch {
            // M5-2: freeRoamActive bleibt AN, bis der Server 2xx antwortet — ein
            // Fehlschlag lässt Session + Track liegen, damit Finish wiederholt wird.
            _uiState.value = _uiState.value.copy(status = RouteStatus.LOADING)
            val track = freeRoamTrack.takeIf { it.size >= 2 }
                ?.map { com.routy.app.logic.route.RouteWalkTrackPoint(lat = it.lat, lng = it.lng, accuracy = 35.0) }
                ?: _uiState.value.trackedGeometry.map {
                    com.routy.app.logic.route.RouteWalkTrackPoint(lat = it.lat, lng = it.lng, accuracy = 35.0)
                }
            val lengthM = trackDistanceM(track)
            // M5-A2: unter 2 Punkten oder unter 50 m lokal abbrechen, bevor gepostet
            // wird (Server-Gegenstück FREE_ROAM_MIN_TRACK_M=50 / schon im Web).
            if (track.size < 2 || lengthM < 50) {
                _uiState.value = _uiState.value.copy(
                    status = RouteStatus.IDLE,
                    messageRes = R.string.free_roam_too_short,
                    messageArgs = emptyList(),
                )
                return@launch
            }
            val durationMin = freeRoamStartedAtMs?.let { ((System.currentTimeMillis() - it) / 60000L).toInt() } ?: 1
            val res = try {
                apiClientProvider.service.completeFreeRoam(
                    FreeRoamCompleteRequest(
                        token = token,
                        trackPoints = track,
                        lengthM = lengthM,
                        durationMin = durationMin,
                        nickname = null,
                    ),
                )
            } catch (_: IOException) {
                _uiState.value = _uiState.value.copy(
                    status = RouteStatus.IDLE,
                    messageRes = R.string.common_error,
                    messageArgs = emptyList(),
                )
                return@launch
            }
            if (res.isSuccessful) {
                freeRoamLastCheckinLat = null
                freeRoamLastCheckinLng = null
                freeRoamTrack.clear()
                freeRoamStartedAtMs = null
                StatsInvalidation.bump()
                _uiState.value = _uiState.value.copy(
                    freeRoamActive = false,
                    freeRoamToken = "",
                    freeRoamStartNodeId = null,
                    freeRoamReachedCount = 0,
                    freeRoamAnnounce = null,
                    trackedGeometry = emptyList(),
                    status = RouteStatus.IDLE,
                    messageRes = R.string.free_roam_finished,
                    messageArgs = emptyList(),
                    trackEnabled = false,
                )
            } else {
                // M5-2/4xx/5xx/410/429: Session + Track bleiben; 429 zeigt die
                // Wartezeit statt „Allgemeiner Fehler“.
                val code = parseErrorCode(res.errorBody()?.string())
                val retry = res.headers()["Retry-After"]?.toIntOrNull()
                _uiState.value = _uiState.value.copy(
                    status = RouteStatus.IDLE,
                    messageRes = if (code == "rate_limited") R.string.free_roam_rate_limited else R.string.common_error,
                    messageArgs = if (code == "rate_limited") listOf(retry ?: 30) else emptyList(),
                )
            }
        }
    }

    private fun maybeCheckinFreeRoam(point: GeoPoint) {
        val token = _uiState.value.freeRoamToken
        if (token.isEmpty() || freeRoamCheckinInFlight) return
        // Grow the free-roam track on every meaningful movement (>=~5 m from the
        // last recorded fix), independent of the checkin throttle — so finish
        // always has a real walked polyline even when tracking stayed off.
        val lastTrack = freeRoamTrack.lastOrNull()
        if (lastTrack != null) {
            val moved = haversineMeters(LatLng(point.lat, point.lng), LatLng(lastTrack.lat, lastTrack.lng))
            if (moved >= 5.0) freeRoamTrack.add(point)
        } else {
            freeRoamTrack.add(point)
        }
        val lastLat = freeRoamLastCheckinLat
        val lastLng = freeRoamLastCheckinLng
        if (lastLat != null && lastLng != null) {
            val d = haversineMeters(LatLng(point.lat, point.lng), LatLng(lastLat, lastLng))
            // Q6 (entschieden): 10 m auf beiden Clients — 30 m können einen Knoten-
            // Eintritt verpassen, wenn man stehen bleibt.
            if (d < 10) return // not moved a meaningful distance yet
        }
        freeRoamCheckinInFlight = true
        viewModelScope.launch {
            try {
                val res = apiClientProvider.service.checkinFreeRoam(FreeRoamCheckinRequest(lat = point.lat, lng = point.lng, token = token))
                if (res.isSuccessful && res.body() != null) {
                    val body = res.body()!!
                    // M5-3: Nur nach 2xx den Cursor vorschieben (sonst wird der
                    // Fehlschlag erst nach der nächsten Bewegung erneut versucht —
                    // und das alte Token nicht „verbraucht“).
                    freeRoamLastCheckinLat = point.lat
                    freeRoamLastCheckinLng = point.lng
                    // Stateless token rotation (decision K): the server re-signs
                    // on every checkin — keep the fresh token for later calls.
                    // (body.token is a cross-module public property, so Kotlin
                    // can't smart-cast it — capture into a local first.)
                    val freshToken = body.token
                    // M5-3: Verwirf die Antwort, wenn inzwischen eine andere
                    // Session begonnen wurde (stop + neuer Start dazwischen) —
                    // der Request lief mit dem alten Token, sein frischer Token darf
                    // die neue Session nicht überschreiben.
                    if (token == _uiState.value.freeRoamToken && !freshToken.isNullOrEmpty()) {
                        _uiState.value = _uiState.value.copy(freeRoamToken = freshToken)
                    }
                    if (token == _uiState.value.freeRoamToken) {
                        if (body.shouldAnnounce && body.newlyReached.isNotEmpty()) {
                            _uiState.value = _uiState.value.copy(
                                freeRoamReachedCount = body.visitedCount,
                                // 0.57: komplette lokalisierte Ansage kommt jetzt vom Server
                                // („Angekommen bei X, mögliche Wege zu… Richtung…“) — einmal,
                                // nicht doppelt, mit Richtungen. Fallback bleibt die Web-Gleichlaut.
                                freeRoamAnnounce = body.announcement ?: announceNeighbours(body.newlyReached),
                            )
                        } else {
                            _uiState.value = _uiState.value.copy(freeRoamReachedCount = body.visitedCount)
                        }
                    }
                }
            } catch (_: IOException) {
                // transient — next location fix retries
            } finally {
                // M5-3: inFlight IMMER löschen (auch bei nicht-IOException-Exceptions,
                // die vorher Check-ins für die Session sterben ließen).
                freeRoamCheckinInFlight = false
            }
        }
    }

    private fun announceText(names: List<String?>): String {
        val nodes = names.mapNotNull { it?.takeIf { s -> s.isNotBlank() } }
        val joined = nodes.joinToString(", ")
        return if (joined.isEmpty()) "" else joined
    }

    // Q7 (entschieden): Dieselbe Nachbarzeile wie Web — "Name über Via" statt nur
    // "Name" — damit die Ansage dasselbe sagt, was der Nutzer auf dem Sheet sieht.
    private fun announceNeighbours(nodes: List<FreeRoamReachedNode>): String =
        announceNeighbourLine(nodes.flatMap { it.reachable })

    private fun announceNeighbourLine(nodes: List<FreeRoamNeighbour>): String =
        nodes.map { nb ->
            val name = nb.name?.takeIf { s -> s.isNotBlank() } ?: "#${nb.nodeId}"
            val via = nb.viaSegmentName?.takeIf { s -> s.isNotBlank() }
            if (via != null) "$name über $via" else name
        }.joinToString(", ")

    private fun trackDistanceM(points: List<com.routy.app.logic.route.RouteWalkTrackPoint>): Int {
        var d = 0.0
        for (i in 1 until points.size) {
            d += haversineMeters(LatLng(points[i - 1].lat, points[i - 1].lng), LatLng(points[i].lat, points[i].lng))
        }
        return d.toInt()
    }

    fun setFollowEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(
            followEnabled = enabled,
            myLocation = if (enabled) _uiState.value.myLocation else null,
            locationBearing = if (enabled) _uiState.value.locationBearing else null,
            locationSpeed = if (enabled) _uiState.value.locationSpeed else null,
        )
    }
    fun setVoiceEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(voiceEnabled = enabled)
    }
    fun setTrackEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(trackEnabled = enabled)
    }
    fun cycleCompassMode() {
        val next = when (_uiState.value.compassMode) {
            MapCompassMode.NORTH_FREE -> MapCompassMode.HEADING_UP
            MapCompassMode.HEADING_UP -> MapCompassMode.NORTH_LOCKED
            MapCompassMode.NORTH_LOCKED -> MapCompassMode.NORTH_FREE
        }
        _uiState.value = _uiState.value.copy(compassMode = next)
    }
    fun setTrackedGeometry(points: List<GeoPoint>) {
        if (points != _uiState.value.trackedGeometry) {
            _uiState.value = _uiState.value.copy(trackedGeometry = points)
        }
    }

    fun trackPointsForComplete(route: RouteDisplayPayload): List<com.routy.app.logic.route.RouteWalkTrackPoint> =
        routeWalkTrackStore.load(routeKey(route))?.points.orEmpty()

    /** Apply progress updates from [RouteTrackingForegroundService] (background-safe). */
    fun syncFromTrackingService(
        completedWaypointIndex: Int,
        voiceAnnouncedIndex: Int,
        trackingActive: Boolean,
        trackEnabled: Boolean,
        myLocation: GeoPoint?,
        locationBearing: Float?,
        locationSpeed: Float?,
        trackedGeometry: List<GeoPoint>,
    ) {
        val state = _uiState.value
        val route = state.route
        val completed = maxOf(state.completedWaypointIndex, completedWaypointIndex)
        val voiceIdx = maxOf(state.voiceAnnouncedIndex, voiceAnnouncedIndex)
        val nextTrack = when {
            !trackingActive -> false
            !trackEnabled && state.trackEnabled -> false
            else -> state.trackEnabled
        }
        if (
            completed == state.completedWaypointIndex &&
            voiceIdx == state.voiceAnnouncedIndex &&
            nextTrack == state.trackEnabled &&
            myLocation == state.myLocation &&
            trackedGeometry == state.trackedGeometry
        ) {
            return
        }
        if (route != null && (completed != state.completedWaypointIndex || voiceIdx != state.voiceAnnouncedIndex)) {
            routeProgressStore.save(routeKey(route), completed, voiceIdx)
        }
        _uiState.value = state.copy(
            completedWaypointIndex = completed,
            voiceAnnouncedIndex = voiceIdx,
            trackEnabled = nextTrack,
            myLocation = myLocation ?: state.myLocation,
            locationBearing = locationBearing ?: state.locationBearing,
            locationSpeed = locationSpeed ?: state.locationSpeed,
            trackedGeometry = trackedGeometry,
            followEnabled = state.followEnabled || myLocation != null,
        )
    }
    fun setKeepScreenOn(enabled: Boolean) { _uiState.value = _uiState.value.copy(keepScreenOn = enabled) }
    fun toggleControls() { _uiState.value = _uiState.value.copy(showControls = !_uiState.value.showControls) }
    fun clearPendingShareUrl() { _uiState.value = _uiState.value.copy(pendingShareUrl = null) }
    fun dismissCompletionStats() {
        _uiState.value = _uiState.value.copy(
            completionPointsEarned = null,
            completionWalkId = null,
            completionRouteSnapshot = null,
            completionFavoriteName = "",
            completionLengthRating = null,
            completionRatingSaving = false,
            completionRatingSaved = false,
            completionStreakMultiplier = null,
            completionCurrentStreak = null,
            completionWeeklyPoints = null,
            completionNewAchievements = emptyList(),
            completionPointBreakdown = null,
            completionGoldenHits = 0,
            completionCelebrationTier = "normal",
            completionGuideMode = false,
        )
    }

    fun onWaypointCompleted(index: Int) {
        val route = _uiState.value.route ?: return
        val voiceIndex = _uiState.value.voiceAnnouncedIndex
        routeProgressStore.save(routeKey(route), index, voiceIndex)
        _uiState.value = _uiState.value.copy(completedWaypointIndex = index)
    }

    /**
     * Manually mark the upcoming station as passed without reaching its radius —
     * e.g. you crossed the node on the far side of the road and don't want to
     * walk back. Advances the active-route progress by exactly one station.
     */
    fun markNextStationPassed() {
        val state = _uiState.value
        val route = state.route ?: return
        if (state.mode != RouteMode.ACTIVE) return
        val next = state.completedWaypointIndex + 1
        if (next >= route.stations.size) return // already past the end
        onWaypointCompleted(next)
    }

    fun onVoiceCueAnnounced(announcedCount: Int) {
        val route = _uiState.value.route ?: return
        routeProgressStore.save(routeKey(route), _uiState.value.completedWaypointIndex, announcedCount)
        _uiState.value = _uiState.value.copy(voiceAnnouncedIndex = announcedCount)
    }

    /** Long-press on map during active walk — open retag editor for a nearby route station. */
    fun onActiveMapLongPress(lat: Double, lng: Double) {
        if (_uiState.value.mode != RouteMode.ACTIVE) return
        val route = _uiState.value.route ?: return
        val tap = LatLng(lat, lng)
        val hit = route.stations
            .map { it to haversineMeters(tap, LatLng(it.lat, it.lng)) }
            .minByOrNull { it.second }
            ?.takeIf { it.second <= STATION_RETAG_RADIUS_M }
            ?.first ?: return
        val node = _uiState.value.nodes.find { it.id == hit.nodeId }
        val stored = LatLng(hit.lat, hit.lng)
        val moveTarget = retagLocationBuffer.clusterMoveTarget(stored)
        _uiState.value = _uiState.value.copy(
            retagNodeId = hit.nodeId,
            retagLat = hit.lat,
            retagLng = hit.lng,
            retagPart1 = node?.namePart1Text ?: node?.name.orEmpty(),
            retagPart2 = node?.namePart2Text.orEmpty(),
            retagOfferMove = moveTarget != null,
            retagMoveLat = moveTarget?.lat,
            retagMoveLng = moveTarget?.lng,
        )
    }

    fun updateRetagPart1(value: String) {
        _uiState.value = _uiState.value.copy(retagPart1 = value)
    }

    fun updateRetagPart2(value: String) {
        _uiState.value = _uiState.value.copy(retagPart2 = value)
    }

    fun dismissRetag() {
        _uiState.value = _uiState.value.copy(
            retagNodeId = null,
            retagLat = null,
            retagLng = null,
            retagPart1 = "",
            retagPart2 = "",
            retagOfferMove = false,
            retagMoveLat = null,
            retagMoveLng = null,
            retagSaving = false,
        )
    }

    fun saveRetagNode() {
        val state = _uiState.value
        val nodeId = state.retagNodeId ?: return
        val part1 = state.retagPart1.trim()
        if (part1.isEmpty()) return
        val part2 = state.retagPart2.trim()
        val composed = if (part2.isEmpty()) part1 else "$part1/$part2"
        viewModelScope.launch {
            _uiState.value = state.copy(retagSaving = true)
            val renameOk = runCatching {
                apiClientProvider.service.renameNode(NodeRenameRequest(nodeId, part1, part2))
            }.getOrNull()?.isSuccessful == true
            var moveOk = true
            if (renameOk && state.retagOfferMove && state.retagMoveLat != null && state.retagMoveLng != null) {
                moveOk = runCatching {
                    apiClientProvider.service.moveNode(NodeMoveRequest(nodeId, state.retagMoveLat, state.retagMoveLng))
                }.getOrNull()?.isSuccessful == true
            }
            if (!renameOk || !moveOk) {
                _uiState.value = state.copy(retagSaving = false, messageRes = R.string.common_error, messageArgs = emptyList())
                return@launch
            }
            val moveLat = if (state.retagOfferMove) state.retagMoveLat else null
            val moveLng = if (state.retagOfferMove) state.retagMoveLng else null
            val updatedNodes = state.nodes.map { node ->
                if (node.id != nodeId) node
                else node.copy(
                    name = composed,
                    namePart1Text = part1,
                    namePart2Text = part2,
                    lat = moveLat ?: node.lat,
                    lng = moveLng ?: node.lng,
                )
            }
            val updatedRoute = state.route?.copy(
                stations = state.route.stations.map { station ->
                    if (station.nodeId != nodeId) station
                    else station.copy(
                        name = composed,
                        speakName = composed,
                        lat = moveLat ?: station.lat,
                        lng = moveLng ?: station.lng,
                    )
                },
            )
            _uiState.value = state.copy(
                nodes = updatedNodes,
                route = updatedRoute,
                retagNodeId = null,
                retagLat = null,
                retagLng = null,
                retagPart1 = "",
                retagPart2 = "",
                retagOfferMove = false,
                retagMoveLat = null,
                retagMoveLng = null,
                retagSaving = false,
            )
        }
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
            val display = checkNotNull(body.display)
            _uiState.value = _uiState.value.copy(
                status = RouteStatus.IDLE,
                sharedRouteName = body.name,
                pendingShareToken = token,
                route = display,
                mode = RouteMode.SUGGESTING,
                messageRes = R.string.route_share_preview,
                goldenHitIds = goldenHitsOnRoute(
                    display.segmentIds,
                    _uiState.value.todayGoldenSegmentIds,
                    _uiState.value.segments,
                ),
                pointPreview = null,
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
                routeWalkTrackStore.clear()
                applyNetworkState(
                    _uiState.value.nodes,
                    _uiState.value.segments,
                    state,
                    GameSummaryDto(_uiState.value.totalPoints, _uiState.value.weeklyPoints, _uiState.value.streakMultiplier),
                    _uiState.value.todayGoldenSegmentIds.toList(),
                    offlineCached = _uiState.value.offlineCached,
                    // M3: homeNodeId ist der Account-Home, NICHT der Routenstart —
                    // sonst wird der Goldene/Start für den Rest des Prozesses zum Home.
                    homeNodeId = _uiState.value.homeNodeId,
                )
                _uiState.value = _uiState.value.copy(
                    mode = RouteMode.ACTIVE,
                    status = RouteStatus.IDLE,
                    pendingShareToken = null,
                    sharedRouteName = null,
                    completedWaypointIndex = -1,
                    voiceAnnouncedIndex = 0,
                    // M3: geteilte Route → Zwischenseite; Go startet Tracking.
                    followEnabled = false,
                    trackEnabled = false,
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

    fun surprise() {
        _uiState.value = _uiState.value.copy(explorerMode = false)
        suggest(preset = "surprise")
    }

    private fun guideOrderedNodeIds(): List<Int> {
        val state = _uiState.value
        val start = state.startNodeId ?: state.homeNodeId ?: return emptyList()
        val rest = state.mustVisitNodeIds.filter { it != start }
        return listOf(start) + rest
    }

    fun startGuide() {
        val orderedNodeIds = guideOrderedNodeIds()
        if (orderedNodeIds.size < 2) {
            _uiState.value = _uiState.value.copy(
                messageRes = R.string.route_guide_need_nodes,
                messageArgs = emptyList(),
            )
            return
        }
        if (_uiState.value.offlineCached) {
            _uiState.value = _uiState.value.copy(
                status = RouteStatus.ERROR,
                messageRes = R.string.route_connection_failed,
                messageArgs = emptyList(),
            )
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                status = RouteStatus.LOADING,
                messageRes = null,
                messageArgs = emptyList(),
                selectedNodeId = null,
                selectedSegmentId = null,
            )
            val response = try {
                apiClientProvider.service.startGuide(GuideStartRequest(orderedNodeIds, loopBack = _uiState.value.isLoop))
            } catch (_: IOException) {
                _uiState.value = _uiState.value.copy(
                    status = RouteStatus.ERROR,
                    messageRes = R.string.route_connection_failed,
                    messageArgs = emptyList(),
                )
                return@launch
            }
            if (response.isSuccessful) {
                applyGeneratedRoute(response.body(), response.body()?.token.orEmpty(), guideMode = true)
            } else {
                val code = parseErrorCode(response.errorBody()?.string())
                val messageRes = when (code) {
                    "constraints_impossible" -> R.string.route_constraints_impossible
                    "too_many_nodes" -> R.string.route_guide_too_many
                    "unreachable_guide_leg" -> R.string.route_unreachable_guide_leg
                    else -> R.string.route_no_route_found
                }
                _uiState.value = _uiState.value.copy(
                    status = RouteStatus.ERROR,
                    messageRes = messageRes,
                    messageArgs = emptyList(),
                )
            }
        }
    }

    fun suggest(preset: String? = null) {
        val state = _uiState.value
        val startNodeId = state.startNodeId ?: state.homeNodeId
        if (startNodeId == null) {
            _uiState.value = state.copy(messageRes = R.string.route_no_home_node, messageArgs = emptyList())
            return
        }
        if (state.offlineCached) {
            _uiState.value = state.copy(status = RouteStatus.ERROR, messageRes = R.string.route_connection_failed, messageArgs = emptyList())
            return
        }
        val surprise = preset == "surprise"
        val explorerMode = if (surprise) false else state.explorerMode
        // Loop when no destination is set; point-to-point otherwise (destination = Ziel).
        val destination = if (state.isLoop || state.destinationNodeId == null) startNodeId else state.destinationNodeId
        val mustVisit = state.mustVisitNodeIds.filter { it != startNodeId && it != destination }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(status = RouteStatus.LOADING, messageRes = null, messageArgs = emptyList(), selectedNodeId = null, selectedSegmentId = null)
            val response = try {
                apiClientProvider.service.generateRoute(
                    GenerateRouteRequest(
                        startNodeId = startNodeId,
                        destinationNodeId = destination,
                        mustVisitNodeIds = mustVisit,
                        requiredSegmentIds = state.requiredSegmentIds,
                        excludedSegmentIds = state.excludedSegmentIds,
                        explorerMode = explorerMode,
                        preset = preset,
                        forceGolden = state.forceGolden,
                    ),
                )
            } catch (_: IOException) {
                _uiState.value = _uiState.value.copy(
                    status = RouteStatus.ERROR,
                    route = state.route,
                    token = state.token,
                    messageRes = R.string.route_connection_failed,
                    messageArgs = emptyList(),
                )
                return@launch
            }
            if (response.isSuccessful) {
                val body = response.body()
                val hitIds = body?.goldenHitIds?.toSet()
                    ?: body?.route?.let {
                        goldenHitsOnRoute(it.segmentIds, _uiState.value.todayGoldenSegmentIds, _uiState.value.segments)
                    }
                    ?: emptySet()
                val lengthRelaxed = body?.lengthRelaxed == true
                val lengthKm = body?.lengthKm ?: body?.route?.lengthM?.let { it / 1000.0 }
                val closedWarn = body?.closedNodeWarnings?.isNotEmpty() == true
                _uiState.value = _uiState.value.copy(
                    status = RouteStatus.IDLE,
                    token = body?.token ?: "",
                    route = body?.route,
                    pointPreview = body?.pointPreview,
                    goldenHitIds = hitIds,
                    usingNetworkFallback = body?.usingNetworkFallback,
                    guideMode = false,
                    pointsMultiplier = null,
                    messageRes = when {
                        closedWarn -> R.string.route_closed_nodes_warning
                        lengthRelaxed && lengthKm != null -> R.string.route_length_relaxed
                        else -> null
                    },
                    messageArgs = if (lengthRelaxed && lengthKm != null && !closedWarn) {
                        listOf(String.format("%.2f", lengthKm))
                    } else {
                        emptyList()
                    },
                )
            } else {
                val errorBody = response.errorBody()?.string()
                val code = parseErrorCode(errorBody)
                val retryAfter = parseRetryAfterSeconds(errorBody)
                val messageRes = when {
                    response.code() == 429 && code == "rate_limited" -> R.string.route_rate_limited
                    code == "no_home_node" -> R.string.route_no_home_node
                    code == "no_golden_route" -> R.string.route_no_golden_route
                    code == "constraints_impossible" -> R.string.route_constraints_impossible
                    else -> R.string.route_no_route_found
                }
                val messageArgs = if (response.code() == 429 && code == "rate_limited") {
                    listOf(retryAfter ?: 60)
                } else {
                    emptyList()
                }
                _uiState.value = _uiState.value.copy(
                    status = RouteStatus.ERROR,
                    route = state.route,
                    token = state.token,
                    messageRes = messageRes,
                    messageArgs = messageArgs,
                )
            }
        }
    }

    fun another() = adjustInternal { apiClientProvider.service.widenRoute(RouteTokenRequest(_uiState.value.token)) }
    fun adjust(direction: String) = adjustInternal { apiClientProvider.service.adjustRoute(AdjustRouteRequest(_uiState.value.token, direction)) }
    fun reverse() = adjustInternal { apiClientProvider.service.reverseRoute(RouteTokenRequest(_uiState.value.token)) }

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
                _uiState.value = _uiState.value.copy(status = RouteStatus.IDLE, messageRes = R.string.route_connection_failed)
                return@launch
            }
            if (response.isSuccessful) {
                applyGeneratedRoute(response.body(), token)
            } else {
                _uiState.value = _uiState.value.copy(status = RouteStatus.IDLE, messageRes = R.string.route_no_alternative)
            }
        }
    }

    private fun applyGeneratedRoute(body: com.routy.app.logic.api.GenerateRouteResponse?, token: String, guideMode: Boolean = body?.guideMode == true) {
        val hitIds = body?.goldenHitIds?.toSet()
            ?: body?.route?.let {
                goldenHitsOnRoute(it.segmentIds, _uiState.value.todayGoldenSegmentIds, _uiState.value.segments)
            }
            ?: emptySet()
        val lengthRelaxed = body?.lengthRelaxed == true
        val lengthKm = body?.lengthKm ?: body?.route?.lengthM?.let { it / 1000.0 }
        _uiState.value = _uiState.value.copy(
            status = RouteStatus.IDLE,
            token = body?.token ?: token,
            route = body?.route,
            pointPreview = body?.pointPreview,
            goldenHitIds = hitIds,
            guideMode = guideMode,
            pointsMultiplier = body?.pointsMultiplier,
            messageRes = if (lengthRelaxed && lengthKm != null) R.string.route_length_relaxed else null,
            messageArgs = if (lengthRelaxed && lengthKm != null) listOf(String.format("%.2f", lengthKm)) else emptyList(),
        )
    }

    /** Route aus der Vorschau übernehmen, ABER noch NICHT mit dem Tracking starten
     *  und noch NICHT serverseitig abschließen (Bestätigungsschritt). „Auswählen“
     *  im Generierungsmenü ruft das auf: Wechselt lokal in den Bestätigungszustand
     *  (Route angenommen-bereit), ohne den Route-Token zu verbrauchen. Der Server-Call
     *  (acceptRoute) + GPS-Tracking-Start passieren erst bei „Go“ via accept().
     *  Dadurch schlägt Go-Start nicht mehr mit „Sitzung abgelaufen“ fehl (vorher
     *  verbrauchte select() den Token schon beim Auswählen). */
    fun select() {
        val state = _uiState.value
        if (state.token.isBlank()) return
        routeProgressStore.clear()
        routeWalkTrackStore.clear()
        prefetchMapTiles(state.route)
        // ACTIVE, aber OHNE Tracking: followEnabled + trackEnabled erst beim Go-Start.
        _uiState.value = state.copy(
            mode = RouteMode.ACTIVE,
            status = RouteStatus.IDLE,
            messageRes = null,
            nickname = "",
            completedWaypointIndex = -1,
            voiceAnnouncedIndex = 0,
            followEnabled = false,
            trackEnabled = false,
        )
    }

    /** Route ENDGÜLTIG starten („Go“ auf der Bestätigungsseite): übernimmt die Route
     *  serverseitig und schaltet das GPS-Tracking an. */
    fun accept() {
        viewModelScope.launch {
            val state = _uiState.value
            val token = state.token
            val guide = state.guideMode
            // M3: Go ohne Token (Favorit/Share/Bootstrap) startet NUR das Tracking —
            // der Server besitzt die Route schon; ein leerer Token bei /accept
            // würde 400 „Sitzung abgelaufen“ werfen.
            if (token.isBlank() && state.route != null) {
                routeProgressStore.clear()
                routeWalkTrackStore.clear()
                prefetchMapTiles(state.route)
                _uiState.value = state.copy(
                    mode = RouteMode.ACTIVE,
                    status = RouteStatus.IDLE,
                    messageRes = null,
                    nickname = state.nickname,
                    completedWaypointIndex = -1,
                    voiceAnnouncedIndex = 0,
                    followEnabled = true,
                    trackEnabled = true,
                    voiceEnabled = state.voiceEnabled,
                    token = "",
                    walkStartedAtMs = System.currentTimeMillis(),
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(status = RouteStatus.LOADING)
            val response = try {
                if (guide) {
                    apiClientProvider.service.acceptGuide(RouteTokenRequest(token))
                } else {
                    apiClientProvider.service.acceptRoute(RouteTokenRequest(token))
                }
            } catch (_: IOException) {
                _uiState.value = _uiState.value.copy(status = RouteStatus.IDLE, messageRes = R.string.route_session_expired)
                return@launch
            }
            if (response.isSuccessful) {
                routeProgressStore.clear()
                routeWalkTrackStore.clear()
                prefetchMapTiles(_uiState.value.route)
                _uiState.value = _uiState.value.copy(mode = RouteMode.ACTIVE, status = RouteStatus.IDLE, messageRes = null, nickname = "", completedWaypointIndex = -1, voiceAnnouncedIndex = 0, followEnabled = true, trackEnabled = true, token = "", walkStartedAtMs = System.currentTimeMillis())
                return@launch
            }
            if (response.code() == 409) {
                // M3: andere Gerät läuft schon — Retry mit replace (Emanuel: Android ist
                // die Wahrheit; ein bestätigtes Go ersetzt die alte Route).
                val retry = try {
                    if (guide) {
                        apiClientProvider.service.acceptGuide(RouteTokenRequest(token, replace = true))
                    } else {
                        apiClientProvider.service.acceptRoute(RouteTokenRequest(token, replace = true))
                    }
                } catch (_: IOException) {
                    _uiState.value = _uiState.value.copy(status = RouteStatus.IDLE, messageRes = R.string.route_session_expired)
                    return@launch
                }
                if (retry.isSuccessful) {
                    routeProgressStore.clear()
                    routeWalkTrackStore.clear()
                    prefetchMapTiles(_uiState.value.route)
                    _uiState.value = _uiState.value.copy(mode = RouteMode.ACTIVE, status = RouteStatus.IDLE, messageRes = null, nickname = "", completedWaypointIndex = -1, voiceAnnouncedIndex = 0, followEnabled = true, trackEnabled = true, token = "", walkStartedAtMs = System.currentTimeMillis())
                } else {
                    _uiState.value = _uiState.value.copy(status = RouteStatus.IDLE, messageRes = R.string.common_error)
                }
                return@launch
            }
            if (response.code() == 410) {
                // M3: Token schon verbraucht — wenn der Server die Route hat, in den
                // Lauf gehen statt „Sitzung abgelaufen“ zu zeigen.
                val resolved = fetchRouteStateWithRetry()
                if (resolved?.activeRoute != null) {
                    routeProgressStore.clear()
                    routeWalkTrackStore.clear()
                    _uiState.value = _uiState.value.copy(
                        mode = RouteMode.ACTIVE,
                        status = RouteStatus.IDLE,
                        messageRes = null,
                        nickname = resolved.nickname ?: "",
                        completedWaypointIndex = -1,
                        voiceAnnouncedIndex = 0,
                        followEnabled = true,
                        trackEnabled = true,
                        token = "",
                        walkStartedAtMs = System.currentTimeMillis(),
                    )
                } else {
                    _uiState.value = _uiState.value.copy(status = RouteStatus.IDLE, messageRes = R.string.route_session_expired)
                }
                return@launch
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

    fun dismissRepositionPicker() {
        _uiState.value = _uiState.value.copy(repositionCandidates = emptyList())
    }

    fun requestReposition() {
        val state = _uiState.value
        val loc = state.myLocation
        if (loc == null) {
            _uiState.value = state.copy(messageRes = R.string.record_location_error, messageArgs = emptyList())
            return
        }
        val accuracy = state.repositionAccuracyM
        val maxDist = accuracy + 30.0
        val nearby = state.nodes
            .map { it to haversineM(it.lat, it.lng, loc.lat, loc.lng) }
            .filter { (_, d) -> d <= maxDist }
            .sortedBy { (_, d) -> d }
            .map { (n, _) -> n.id }
        if (nearby.isEmpty()) {
            _uiState.value = state.copy(messageRes = R.string.common_error, messageArgs = emptyList())
            return
        }
        if (nearby.size == 1) {
            confirmReposition(nearby.first())
            return
        }
        _uiState.value = state.copy(repositionCandidates = nearby)
    }

    fun confirmReposition(nodeId: Int) {
        val state = _uiState.value
        val loc = state.myLocation ?: return
        viewModelScope.launch {
            _uiState.value = state.copy(status = RouteStatus.LOADING, repositionCandidates = emptyList())
            val response = try {
                apiClientProvider.service.repositionNode(
                    RepositionNodeRequest(nodeId, loc.lat, loc.lng, state.repositionAccuracyM),
                )
            } catch (_: IOException) {
                _uiState.value = _uiState.value.copy(status = RouteStatus.IDLE, messageRes = R.string.common_error)
                return@launch
            }
            if (response.isSuccessful) {
                val offPath = response.body()?.offPathWarning == true
                when (val result = bootstrapLoader.load()) {
                    is BootstrapResult.Fresh -> applyNetworkState(
                        result.body.nodes,
                        result.body.segments,
                        result.body.routeState,
                        result.body.game,
                        result.body.todayGoldenSegmentIds,
                        offlineCached = false,
                        homeNodeId = result.body.user.homeNodeId,
                    )
                    is BootstrapResult.NotModified, is BootstrapResult.CachedOnly -> {
                        val cached = when (result) {
                            is BootstrapResult.NotModified -> result.cached
                            is BootstrapResult.CachedOnly -> result.cached
                            else -> null
                        }
                        if (cached != null) {
                            applyNetworkState(
                                cached.nodes,
                                cached.segments,
                                cached.routeState,
                                cached.game,
                                cached.todayGoldenSegmentIds,
                                offlineCached = true,
                                homeNodeId = cached.user.homeNodeId,
                            )
                        }
                    }
                    BootstrapResult.Unauthorized, BootstrapResult.Failed -> Unit
                }
                _uiState.value = _uiState.value.copy(
                    status = RouteStatus.IDLE,
                    messageRes = if (offPath) R.string.route_reposition_off_path else R.string.route_reposition_success,
                    messageArgs = emptyList(),
                )
            } else {
                _uiState.value = _uiState.value.copy(status = RouteStatus.IDLE, messageRes = R.string.common_error)
            }
        }
    }

    private fun haversineM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371000.0
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dP = Math.toRadians(lat2 - lat1)
        val dL = Math.toRadians(lng2 - lng1)
        val a = Math.sin(dP / 2) * Math.sin(dP / 2) +
            Math.cos(p1) * Math.cos(p2) * Math.sin(dL / 2) * Math.sin(dL / 2)
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }

    fun cancel() {
        val token = _uiState.value.token
        if (_uiState.value.route == null) return
        viewModelScope.launch {
            try { apiClientProvider.service.cancelRoute(RouteTokenRequest(token)) } catch (_: IOException) {}
            _uiState.value = _uiState.value.copy(route = null, token = "", status = RouteStatus.IDLE, messageRes = null, pointPreview = null, goldenHitIds = emptySet(), guideMode = false, pointsMultiplier = null, walkStartedAtMs = null)
        }
    }

    fun complete() {
        viewModelScope.launch {
            val stateBefore = _uiState.value
            val completedRoute = stateBefore.route
            // M4-A2: NICHT trackEnabled/voiceEnabled abschalten — der Service muss
            // weiterlaufen, bis complete() 2xx antwortet (sonst killt needsService
            // den Foreground-Service mitten im Request; Fehler ließen GPS+Track
            // liegen statt sie für den Retry laufen zu lassen).
            _uiState.value = stateBefore.copy(status = RouteStatus.LOADING)
            val beforeAchievements = runCatching {
                apiClientProvider.service.appStatsMe()
                    .takeIf { it.isSuccessful }
                    ?.body()
                    ?.achievements
            }.getOrNull()
            val route = _uiState.value.route
            val trackPoints = route?.let { trackPointsForComplete(it) }.orEmpty()
            // M4: Ist-Dauer seit dem Go (Tracking-Start), sonst null → Server nutzt
            // accepted_at-Elapsed.
            val startedAt = _uiState.value.walkStartedAtMs
            val durationMin = startedAt?.let {
                kotlin.math.max(1, ((System.currentTimeMillis() - it) / 60_000L).toInt())
            }
            val response = try {
                apiClientProvider.service.completeRoute(
                    CompleteRouteRequest(trackPoints = trackPoints, durationMin = durationMin),
                )
            } catch (_: IOException) {
                _uiState.value = stateBefore.copy(
                    status = RouteStatus.IDLE,
                    messageRes = R.string.common_error,
                    messageArgs = emptyList(),
                )
                return@launch
            }
            if (response.isSuccessful) {
                routeProgressStore.clear()
                routeWalkTrackStore.clear()
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
                    messageArgs = emptyList(),
                    nickname = "",
                    followEnabled = false,
                    trackEnabled = false,
                    voiceEnabled = false,
                    myLocation = null,
                    trackedGeometry = emptyList(),
                    walkStartedAtMs = null,
                    completedWaypointIndex = -1,
                    voiceAnnouncedIndex = 0,
                    pointPreview = null,
                    goldenHitIds = emptySet(),
                    totalPoints = afterStats?.points?.totalPoints ?: (_uiState.value.totalPoints + (body?.pointsEarned ?: 0)),
                    weeklyPoints = afterStats?.points?.weeklyPoints ?: _uiState.value.weeklyPoints,
                    streakMultiplier = body?.streakMultiplier ?: _uiState.value.streakMultiplier,
                    completionPointsEarned = body?.pointsEarned,
                    completionWalkId = body?.walkId,
                    completionRouteSnapshot = completedRoute,
                    completionFavoriteName = "",
                    completionLengthRating = null,
                    completionRatingSaving = false,
                    completionRatingSaved = false,
                    completionStreakMultiplier = body?.streakMultiplier,
                    completionCurrentStreak = body?.currentStreak,
                    completionWeeklyPoints = afterStats?.points?.weeklyPoints,
                    completionNewAchievements = diffNewAchievements(beforeAchievements, afterStats?.achievements),
                    completionPointBreakdown = body?.pointBreakdown,
                    completionGoldenHits = body?.goldenHits ?: 0,
                    completionCelebrationTier = body?.celebrationTier ?: "normal",
                    completionGuideMode = body?.guideMode == true || _uiState.value.guideMode,
                    guideMode = false,
                    pointsMultiplier = null,
                )
            } else {
                // M4-A2/429: Service läuft weiter; Session + Track bleiben. 429 zeigt
                // die Wartezeit (retryAfterSeconds) statt „Allgemeiner Fehler“.
                val errorBody = response.errorBody()?.string()
                val code = parseErrorCode(errorBody)
                val retryAfter = parseRetryAfterSeconds(errorBody)
                _uiState.value = stateBefore.copy(
                    status = RouteStatus.IDLE,
                    messageRes = if (response.code() == 429 && code == "rate_limited") R.string.route_rate_limited else R.string.common_error,
                    messageArgs = if (response.code() == 429 && code == "rate_limited") listOf(retryAfter ?: 30) else emptyList(),
                )
            }
        }
    }

    fun submitLengthRating(rating: Int) {
        val walkId = _uiState.value.completionWalkId ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(completionLengthRating = rating, completionRatingSaving = true)
            val response = try {
                apiClientProvider.service.rateWalk(RateWalkRequest(walkId, rating))
            } catch (_: IOException) {
                _uiState.value = _uiState.value.copy(completionRatingSaving = false, messageRes = R.string.common_error, messageArgs = emptyList())
                return@launch
            }
            _uiState.value = _uiState.value.copy(
                completionRatingSaving = false,
                completionRatingSaved = response.isSuccessful,
                messageRes = if (response.isSuccessful) null else R.string.common_error,
                messageArgs = emptyList(),
            )
        }
    }

    fun saveFavoriteAfterComplete() {
        val route = _uiState.value.completionRouteSnapshot ?: return
        val name = _uiState.value.completionFavoriteName.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(savingFavorite = true)
            val response = try {
                apiClientProvider.service.saveFavorite(
                    SaveFavoriteRequest(name, route.nodeChain, route.segmentIds, route.lengthM, route.durationMin),
                )
            } catch (_: IOException) {
                _uiState.value = _uiState.value.copy(savingFavorite = false, messageRes = R.string.common_error, messageArgs = emptyList())
                return@launch
            }
            _uiState.value = _uiState.value.copy(
                savingFavorite = false,
                completionFavoriteName = if (response.isSuccessful) "" else _uiState.value.completionFavoriteName,
                messageRes = if (response.isSuccessful) R.string.route_favorite_saved else R.string.common_error,
                messageArgs = emptyList(),
            )
            if (response.isSuccessful) refreshFavorites()
        }
    }

    fun discardActive() {
        viewModelScope.launch {
            val state = _uiState.value
            // M3-6/Q1: Auf der Zwischenseite (nicht gestartet, trackEnabled=false):
            //  - Token gesetzt (frische generierte Route): NUR lokal zurück — der
            //    Server kennt die Route noch nicht, kein /discard nötig, der
            //    Vorschlag bleibt (Q1-Empfehlung: zurück zu SUGGESTING, Route bleibt).
            //  - Token leer (Favorit/Share/Bootstrap): Route hängt am Account →
            //    serverseitig discard.
            // Live (trackEnabled): normaler Serverseiten-Discard.
            val isPreview = state.mode == RouteMode.ACTIVE && !state.trackEnabled
            if (isPreview && state.token.isNotBlank()) {
                _uiState.value = state.copy(
                    mode = RouteMode.SUGGESTING,
                    status = RouteStatus.IDLE,
                    messageRes = null,
                    trackEnabled = false,
                    voiceEnabled = false,
                    nickname = "",
                    followEnabled = false,
                    completedWaypointIndex = -1,
                    voiceAnnouncedIndex = 0,
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(status = RouteStatus.LOADING, trackEnabled = false, voiceEnabled = false)
            try { apiClientProvider.service.discardRoute() } catch (_: IOException) {}
            routeProgressStore.clear()
            routeWalkTrackStore.clear()
            _uiState.value = _uiState.value.copy(
                route = null,
                mode = RouteMode.SUGGESTING,
                status = RouteStatus.IDLE,
                messageRes = null,
                nickname = "",
                followEnabled = false,
                trackEnabled = false,
                voiceEnabled = false,
                myLocation = null,
                trackedGeometry = emptyList(),
                completedWaypointIndex = -1,
                voiceAnnouncedIndex = 0,
                pointPreview = null,
                goldenHitIds = emptySet(),
                guideMode = false,
                pointsMultiplier = null,
                walkStartedAtMs = null,
            )
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
                routeWalkTrackStore.clear()
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
                    // M3: Favorit → Zwischenseite, NICHT sofort folgen/aufzeichnen.
                    // Go (accept mit leerem Token) startet Tracking erst.
                    followEnabled = false,
                    trackEnabled = false,
                    goldenHitIds = goldenHitsOnRoute(
                        favorite.display.segmentIds,
                        _uiState.value.todayGoldenSegmentIds,
                        _uiState.value.segments,
                    ),
                    pointPreview = null,
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
            pointPreview = null,
            goldenHitIds = emptySet(),
            walkStartedAtMs = null,
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

    private fun parseRetryAfterSeconds(errorBodyJson: String?): Int? {
        if (errorBodyJson == null) return null
        return try { errorJson.decodeFromString(ApiErrorBody.serializer(), errorBodyJson).retryAfterSeconds } catch (_: Exception) { null }
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

private const val PLANNING_NODE_TAP_RADIUS_M = 45.0
private const val STATION_RETAG_RADIUS_M = 45.0
