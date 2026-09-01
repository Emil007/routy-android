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
            followEnabled = state?.activeRoute != null,
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
            destinationNodeId = if (_uiState.value.isLoop) id else _uiState.value.destinationNodeId,
        )
    }
    fun setIsLoop(loop: Boolean) {
        val state = _uiState.value
        _uiState.value = state.copy(
            isLoop = loop,
            destinationNodeId = if (loop) state.startNodeId else state.destinationNodeId,
        )
    }
    fun setDestinationNodeId(id: Int) { _uiState.value = _uiState.value.copy(destinationNodeId = id) }
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
        _uiState.value = state.copy(
            startNodeId = if (state.startNodeId == nodeId) home else state.startNodeId,
            destinationNodeId = if (state.destinationNodeId == nodeId) home else state.destinationNodeId,
            mustVisitNodeIds = state.mustVisitNodeIds.filter { it != nodeId },
        )
    }

    fun setNodeAsStart(nodeId: Int) {
        clearNodeRole(nodeId)
        val state = _uiState.value
        _uiState.value = state.copy(
            startNodeId = nodeId,
            destinationNodeId = if (state.isLoop) nodeId else state.destinationNodeId,
            selectedNodeId = null,
        )
    }

    fun setNodeAsEnd(nodeId: Int) {
        clearNodeRole(nodeId)
        _uiState.value = _uiState.value.copy(destinationNodeId = nodeId, selectedNodeId = null)
    }

    fun toggleMustVisit(nodeId: Int) {
        val state = _uiState.value
        if (state.mustVisitNodeIds.contains(nodeId)) {
            _uiState.value = state.copy(
                mustVisitNodeIds = state.mustVisitNodeIds.filter { it != nodeId },
                selectedNodeId = null,
            )
        } else {
            _uiState.value = state.copy(
                startNodeId = if (state.startNodeId == nodeId) state.homeNodeId else state.startNodeId,
                destinationNodeId = if (state.destinationNodeId == nodeId) state.homeNodeId else state.destinationNodeId,
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
    fun setMyLocation(point: GeoPoint?) { _uiState.value = _uiState.value.copy(myLocation = point) }
    fun addRetagLocationSample(lat: Double, lng: Double, accuracyM: Float?) {
        retagLocationBuffer.addSample(lat, lng, accuracyM)
    }
    fun setLocationMotion(bearing: Float?, speed: Float?) {
        _uiState.value = _uiState.value.copy(locationBearing = bearing, locationSpeed = speed)
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
                    homeNodeId = _uiState.value.startNodeId,
                )
                _uiState.value = _uiState.value.copy(
                    mode = RouteMode.ACTIVE,
                    status = RouteStatus.IDLE,
                    pendingShareToken = null,
                    sharedRouteName = null,
                    completedWaypointIndex = -1,
                    voiceAnnouncedIndex = 0,
                    followEnabled = true,
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
                apiClientProvider.service.startGuide(GuideStartRequest(orderedNodeIds))
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
        val destination = if (state.isLoop) startNodeId else (state.destinationNodeId ?: startNodeId)
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
                _uiState.value = _uiState.value.copy(
                    status = RouteStatus.IDLE,
                    token = body?.token ?: "",
                    route = body?.route,
                    pointPreview = body?.pointPreview,
                    goldenHitIds = hitIds,
                    usingNetworkFallback = body?.usingNetworkFallback,
                    guideMode = false,
                    pointsMultiplier = null,
                    messageRes = if (lengthRelaxed && lengthKm != null) R.string.route_length_relaxed else null,
                    messageArgs = if (lengthRelaxed && lengthKm != null) listOf(String.format("%.2f", lengthKm)) else emptyList(),
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

    fun accept() {
        val token = _uiState.value.token
        val guide = _uiState.value.guideMode
        viewModelScope.launch {
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
                _uiState.value = _uiState.value.copy(mode = RouteMode.ACTIVE, status = RouteStatus.IDLE, messageRes = null, nickname = "", completedWaypointIndex = -1, voiceAnnouncedIndex = 0, followEnabled = true)
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
            _uiState.value = _uiState.value.copy(route = null, token = "", status = RouteStatus.IDLE, messageRes = null, pointPreview = null, goldenHitIds = emptySet(), guideMode = false, pointsMultiplier = null)
        }
    }

    fun complete() {
        viewModelScope.launch {
            val completedRoute = _uiState.value.route
            _uiState.value = _uiState.value.copy(status = RouteStatus.LOADING, trackEnabled = false, voiceEnabled = false)
            val beforeAchievements = runCatching {
                apiClientProvider.service.appStatsMe()
                    .takeIf { it.isSuccessful }
                    ?.body()
                    ?.achievements
            }.getOrNull()
            val route = _uiState.value.route
            val trackPoints = route?.let { trackPointsForComplete(it) }.orEmpty()
            val response = try {
                apiClientProvider.service.completeRoute(CompleteRouteRequest(trackPoints = trackPoints))
            } catch (_: IOException) {
                _uiState.value = _uiState.value.copy(status = RouteStatus.IDLE, messageRes = R.string.common_error, messageArgs = emptyList())
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
                _uiState.value = _uiState.value.copy(status = RouteStatus.IDLE, messageRes = R.string.common_error, messageArgs = emptyList())
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
                    followEnabled = true,
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
