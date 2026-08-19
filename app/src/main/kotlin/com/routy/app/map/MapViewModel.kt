package com.routy.app.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routy.app.R
import com.routy.app.core.BootstrapLoader
import com.routy.app.core.BootstrapResult
import com.routy.app.core.network.ApiClientProvider
import com.routy.app.core.storage.NetworkCache
import com.routy.app.logic.api.GeoPoint
import com.routy.app.logic.api.GpxCommitRequest
import com.routy.app.logic.api.GpxEndpoint
import com.routy.app.logic.api.GpxParseTrackPreview
import com.routy.app.logic.api.GpxPoint
import com.routy.app.logic.api.GpxTrack
import com.routy.app.logic.api.NodeDto
import com.routy.app.logic.api.NodeIdRequest
import com.routy.app.logic.api.NodeMoveRequest
import com.routy.app.logic.api.NodeRenameRequest
import com.routy.app.logic.api.PathProposalDto
import com.routy.app.logic.api.ReportConditionRequest
import com.routy.app.logic.api.SegmentConditionDto
import com.routy.app.logic.api.SegmentDto
import com.routy.app.logic.api.SegmentGeometryRequest
import com.routy.app.logic.api.SegmentIdRequest
import com.routy.app.logic.api.SegmentLockRequest
import com.routy.app.logic.api.SegmentRenameRequest
import com.routy.app.logic.api.SegmentSplitRequest
import com.routy.app.logic.api.SessionUser
import com.routy.app.logic.api.isCanonical
import com.routy.app.logic.geo.LatLng
import com.routy.app.logic.geo.closestPointOnPath
import com.routy.app.logic.geo.estimateMinutes
import com.routy.app.logic.geo.findSegmentAtTap
import com.routy.app.logic.geo.haversineMeters
import com.routy.app.logic.geo.pathLengthMeters
import com.routy.app.logic.ownership.canEdit
import com.routy.app.logic.recording.EndpointDecision
import com.routy.app.logic.recording.findNodeCandidates
import com.routy.app.logic.recording.initialEndpointDecision
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import kotlin.math.roundToInt

private const val NODE_TAP_RADIUS_M = 45.0

enum class MapMode { View, Draw, Gpx, EditSegment, SplitSegment }

enum class DrawPhase { Drawing, Confirm }

data class MapUiState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val offlineCached: Boolean = false,
    val user: SessionUser? = null,
    val mergeRadiusM: Double = 50.0,
    val walkSpeedKmh: Double = 5.0,
    val nodes: List<NodeDto> = emptyList(),
    val segments: List<SegmentDto> = emptyList(),
    val segmentConditions: List<SegmentConditionDto> = emptyList(),
    val proposals: List<PathProposalDto> = emptyList(),
    val reportingCondition: Boolean = false,
    val conditionReason: String = "muddy",
    val mode: MapMode = MapMode.View,
    val selectedNode: NodeDto? = null,
    val selectedSegment: SegmentDto? = null,
    val moveNodeId: Int? = null,
    val renamingNode: Boolean = false,
    val renamePart1: String = "",
    val renamePart2: String = "",
    val renamingSegment: Boolean = false,
    val renameSegmentName: String = "",
    val drawPoints: List<LatLng> = emptyList(),
    val drawPhase: DrawPhase = DrawPhase.Drawing,
    val drawStartDecision: EndpointDecision? = null,
    val drawEndDecision: EndpointDecision? = null,
    val drawMarkStartAsHome: Boolean = false,
    val drawSnapEnabled: Boolean = true,
    val editSegmentPoints: List<GeoPoint>? = null,
    val selectedEditVertexIndex: Int? = null,
    val moveEditVertexIndex: Int? = null,
    val splitTarget: LatLng? = null,
    val splitDecision: EndpointDecision? = null,
    val gpxTracks: List<GpxParseTrackPreview>? = null,
    val gpxDecisions: List<Pair<EndpointDecision, EndpointDecision>> = emptyList(),
    val gpxSkip: List<Boolean> = emptyList(),
    val actionBusy: Boolean = false,
    val messageRes: Int? = null,
    val isError: Boolean = false,
)

class MapViewModel(
    private val networkCache: NetworkCache,
    private val bootstrapLoader: BootstrapLoader,
    private val apiClientProvider: ApiClientProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    init {
        loadNetwork()
        loadGpxConfig()
    }

    fun refresh() {
        bootstrapLoader.invalidate()
        _uiState.value = _uiState.value.copy(loading = true, loadFailed = false)
        loadNetwork(forceRefresh = true)
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(messageRes = null, isError = false)
    }

    fun setMode(mode: MapMode) {
        _uiState.value = _uiState.value.copy(
            mode = mode,
            moveNodeId = null,
            renamingNode = false,
            renamingSegment = false,
            drawPoints = if (mode == MapMode.Draw) _uiState.value.drawPoints else emptyList(),
            drawPhase = if (mode == MapMode.Draw) _uiState.value.drawPhase else DrawPhase.Drawing,
            editSegmentPoints = if (mode == MapMode.EditSegment) _uiState.value.editSegmentPoints else null,
            selectedEditVertexIndex = null,
            moveEditVertexIndex = null,
            splitTarget = null,
            splitDecision = null,
            gpxTracks = if (mode == MapMode.Gpx) _uiState.value.gpxTracks else null,
        )
    }

    fun onMapClick(lat: Double, lng: Double) {
        val state = _uiState.value
        val point = LatLng(lat, lng)
        when (state.mode) {
            MapMode.Draw -> if (state.drawPhase == DrawPhase.Drawing) addDrawPoint(point)
            MapMode.EditSegment -> state.editSegmentPoints?.let { handleEditSegmentTap(point, it) }
            MapMode.SplitSegment -> state.selectedSegment?.let { pickSplitPoint(it, point) }
            MapMode.View -> handleViewTap(point)
            MapMode.Gpx -> {}
        }
    }

    private fun handleViewTap(point: LatLng) {
        val state = _uiState.value
        state.moveNodeId?.let { nodeId ->
            moveNodeTo(nodeId, point.lat, point.lng)
            return
        }
        val nearestNode = state.nodes
            .map { it to haversineMeters(point, LatLng(it.lat, it.lng)) }
            .minByOrNull { it.second }
            ?.takeIf { it.second <= NODE_TAP_RADIUS_M }
            ?.first
        if (nearestNode != null) {
            _uiState.value = state.copy(selectedNode = nearestNode, selectedSegment = null, renamingNode = false)
            return
        }
        val segmentHit = findSegmentAtTap(state.segments, point)
        if (segmentHit != null) {
            _uiState.value = state.copy(selectedSegment = segmentHit.first, selectedNode = null, renamingSegment = false)
            return
        }
        _uiState.value = state.copy(selectedNode = null, selectedSegment = null)
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(
            selectedNode = null,
            selectedSegment = null,
            moveNodeId = null,
            renamingNode = false,
            renamingSegment = false,
        )
    }

    fun canEditNode(node: NodeDto): Boolean {
        val user = _uiState.value.user ?: return false
        return canEdit(user.id, user.role == "admin", node.createdBy)
    }

    fun canEditSegment(segment: SegmentDto): Boolean {
        val user = _uiState.value.user ?: return false
        return canEdit(user.id, user.role == "admin", segment.submittedBy)
    }

    fun startRenameNode() {
        val node = _uiState.value.selectedNode ?: return
        _uiState.value = _uiState.value.copy(
            renamingNode = true,
            renamePart1 = node.namePart1Text ?: node.name.orEmpty(),
            renamePart2 = node.namePart2Text.orEmpty(),
        )
    }

    fun updateRenamePart1(value: String) {
        _uiState.value = _uiState.value.copy(renamePart1 = value)
    }

    fun updateRenamePart2(value: String) {
        _uiState.value = _uiState.value.copy(renamePart2 = value)
    }

    fun saveRenameNode() {
        val node = _uiState.value.selectedNode ?: return
        if (!canEditNode(node)) return denyNotAllowed()
        if (_uiState.value.renamePart1.trim().isEmpty()) return
        runMutation(R.string.map_saved) {
            apiClientProvider.service.renameNode(
                NodeRenameRequest(node.id, _uiState.value.renamePart1.trim(), _uiState.value.renamePart2.trim()),
            )
        }
    }

    fun setHomeNode() {
        val node = _uiState.value.selectedNode ?: return
        runMutation(R.string.map_saved) {
            apiClientProvider.service.setHomeNode(NodeIdRequest(node.id))
        }
    }

    fun toggleMoveNode() {
        val node = _uiState.value.selectedNode ?: return
        if (!canEditNode(node)) return denyNotAllowed()
        _uiState.value = _uiState.value.copy(
            moveNodeId = if (_uiState.value.moveNodeId == node.id) null else node.id,
        )
    }

    fun deleteSelectedNode() {
        val node = _uiState.value.selectedNode ?: return
        if (!canEditNode(node)) return denyNotAllowed()
        runMutation(R.string.map_deleted) {
            apiClientProvider.service.deleteNode(NodeIdRequest(node.id))
        }
    }

    private fun moveNodeTo(nodeId: Int, lat: Double, lng: Double) {
        val node = _uiState.value.nodes.find { it.id == nodeId } ?: return
        if (!canEditNode(node)) return denyNotAllowed()
        runMutation(R.string.map_saved) {
            apiClientProvider.service.moveNode(NodeMoveRequest(nodeId, lat, lng))
        }
    }

    fun startRenameSegment() {
        val segment = _uiState.value.selectedSegment ?: return
        _uiState.value = _uiState.value.copy(
            renamingSegment = true,
            renameSegmentName = segment.name.orEmpty(),
        )
    }

    fun updateRenameSegmentName(value: String) {
        _uiState.value = _uiState.value.copy(renameSegmentName = value)
    }

    fun saveRenameSegment() {
        val segment = _uiState.value.selectedSegment ?: return
        if (!canEditSegment(segment)) return denyNotAllowed()
        runMutation(R.string.map_saved) {
            apiClientProvider.service.renameSegment(
                SegmentRenameRequest(segment.id, _uiState.value.renameSegmentName.trim()),
            )
        }
    }

    fun lockSegment(days: Int?) {
        val segment = _uiState.value.selectedSegment ?: return
        if (!canEditSegment(segment)) return denyNotAllowed()
        runMutation(R.string.map_saved) {
            apiClientProvider.service.lockSegment(SegmentLockRequest(segment.id, days))
        }
    }

    fun deleteSelectedSegment() {
        val segment = _uiState.value.selectedSegment ?: return
        if (!canEditSegment(segment)) return denyNotAllowed()
        runMutation(R.string.map_deleted) {
            apiClientProvider.service.deleteSegment(SegmentIdRequest(segment.id))
        }
    }

    fun startEditSegmentShape() {
        val segment = _uiState.value.selectedSegment ?: return
        if (!canEditSegment(segment)) return denyNotAllowed()
        _uiState.value = _uiState.value.copy(
            mode = MapMode.EditSegment,
            editSegmentPoints = segment.geometry,
            selectedNode = null,
            selectedEditVertexIndex = null,
            moveEditVertexIndex = null,
        )
    }

    fun startSplitSegment() {
        val segment = _uiState.value.selectedSegment ?: return
        if (!canEditSegment(segment)) return denyNotAllowed()
        _uiState.value = _uiState.value.copy(
            mode = MapMode.SplitSegment,
            splitTarget = null,
            splitDecision = null,
            selectedNode = null,
        )
    }

    fun finishEditSegmentShape() {
        val segment = _uiState.value.selectedSegment ?: return
        if (!canEditSegment(segment)) return denyNotAllowed()
        val points = _uiState.value.editSegmentPoints ?: return
        if (points.size < 2) return
        runMutation(R.string.map_saved) {
            apiClientProvider.service.updateSegmentGeometry(
                SegmentGeometryRequest(segment.id, points.map { GpxPoint(it.lat, it.lng) }),
            )
        }
    }

    fun cancelEditSegmentShape() {
        setMode(MapMode.View)
    }

    private fun handleEditSegmentTap(tap: LatLng, current: List<GeoPoint>) {
        val state = _uiState.value
        state.moveEditVertexIndex?.let { idx ->
            if (idx in current.indices) {
                val updated = current.toMutableList()
                updated[idx] = GeoPoint(tap.lat, tap.lng)
                _uiState.value = state.copy(
                    editSegmentPoints = updated,
                    moveEditVertexIndex = null,
                    selectedEditVertexIndex = idx,
                )
            }
            return
        }
        findNearestVertexIndex(tap, current)?.let { idx ->
            if (idx > 0 && idx < current.lastIndex) {
                _uiState.value = state.copy(selectedEditVertexIndex = idx)
                return
            }
        }
        addEditPoint(tap, current)
    }

    private fun findNearestVertexIndex(point: LatLng, vertices: List<GeoPoint>, radiusM: Double = 28.0): Int? {
        var best: Pair<Int, Double>? = null
        vertices.forEachIndexed { index, v ->
            val d = haversineMeters(point, LatLng(v.lat, v.lng))
            if (d <= radiusM && (best == null || d < best!!.second)) best = index to d
        }
        return best?.first
    }

    fun toggleMoveEditVertex() {
        val idx = _uiState.value.selectedEditVertexIndex ?: return
        if (idx <= 0 || idx >= (_uiState.value.editSegmentPoints?.lastIndex ?: 0)) return
        _uiState.value = _uiState.value.copy(
            moveEditVertexIndex = if (_uiState.value.moveEditVertexIndex == idx) null else idx,
        )
    }

    fun deleteSelectedEditVertex() {
        val state = _uiState.value
        val idx = state.selectedEditVertexIndex ?: return
        val points = state.editSegmentPoints ?: return
        if (idx <= 0 || idx >= points.lastIndex || points.size <= 2) return
        val updated = points.toMutableList()
        updated.removeAt(idx)
        _uiState.value = state.copy(editSegmentPoints = updated, selectedEditVertexIndex = null, moveEditVertexIndex = null)
    }

    fun setDrawSnapEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(drawSnapEnabled = enabled)
    }

    private fun addEditPoint(tap: LatLng, current: List<GeoPoint>) {
        val path = current.map { LatLng(it.lat, it.lng) }
        val hit = closestPointOnPath(path, tap) ?: return
        val updated = current.toMutableList()
        updated.add(hit.index + 1, GeoPoint(hit.point.lat, hit.point.lng))
        _uiState.value = _uiState.value.copy(editSegmentPoints = updated)
    }

    private fun pickSplitPoint(segment: SegmentDto, tap: LatLng) {
        val path = segment.geometry.map { LatLng(it.lat, it.lng) }
        val hit = closestPointOnPath(path, tap) ?: return
        val decision = initialEndpointDecision(hit.point, _uiState.value.nodes, _uiState.value.mergeRadiusM)
        _uiState.value = _uiState.value.copy(splitTarget = hit.point, splitDecision = decision)
    }

    fun updateSplitDecision(decision: EndpointDecision) {
        _uiState.value = _uiState.value.copy(splitDecision = decision)
    }

    fun confirmSplit() {
        val segment = _uiState.value.selectedSegment ?: return
        if (!canEditSegment(segment)) return denyNotAllowed()
        val target = _uiState.value.splitTarget ?: return
        val decision = _uiState.value.splitDecision ?: return
        val endpointJson = when (decision) {
            is EndpointDecision.Existing -> buildJsonObject { put("nodeId", decision.nodeId) }
            is EndpointDecision.NewJunction -> buildJsonObject {
                put("part1", decision.part1)
                put("part2", decision.part2)
            }
        }
        runMutation(R.string.map_saved) {
            apiClientProvider.service.splitSegment(
                SegmentSplitRequest(segment.id, target.lat, target.lng, endpointJson),
            )
        }
    }

    fun cancelSplit() {
        setMode(MapMode.View)
    }

    private fun addDrawPoint(point: LatLng) {
        val state = _uiState.value
        val placed = if (state.drawSnapEnabled) {
            findNodeCandidates(state.nodes, point, state.mergeRadiusM).firstOrNull()
                ?.let { LatLng(it.lat, it.lng) } ?: point
        } else {
            point
        }
        _uiState.value = state.copy(drawPoints = state.drawPoints + placed)
    }

    fun undoDrawPoint() {
        val pts = _uiState.value.drawPoints
        if (pts.isNotEmpty()) _uiState.value = _uiState.value.copy(drawPoints = pts.dropLast(1))
    }

    fun clearDrawPoints() {
        _uiState.value = _uiState.value.copy(drawPoints = emptyList(), drawPhase = DrawPhase.Drawing)
    }

    fun finishDraw() {
        val pts = _uiState.value.drawPoints
        if (pts.size < 2) return
        _uiState.value = _uiState.value.copy(
            drawPhase = DrawPhase.Confirm,
            drawStartDecision = initialEndpointDecision(pts.first(), _uiState.value.nodes, _uiState.value.mergeRadiusM),
            drawEndDecision = initialEndpointDecision(pts.last(), _uiState.value.nodes, _uiState.value.mergeRadiusM),
        )
    }

    fun updateDrawStartDecision(decision: EndpointDecision) {
        _uiState.value = _uiState.value.copy(drawStartDecision = decision)
    }

    fun updateDrawEndDecision(decision: EndpointDecision) {
        _uiState.value = _uiState.value.copy(drawEndDecision = decision)
    }

    fun setDrawMarkStartAsHome(checked: Boolean) {
        _uiState.value = _uiState.value.copy(drawMarkStartAsHome = checked)
    }

    fun backToDraw() {
        _uiState.value = _uiState.value.copy(drawPhase = DrawPhase.Drawing)
    }

    fun saveDrawPath() {
        val state = _uiState.value
        val start = state.drawStartDecision ?: return
        val end = state.drawEndDecision ?: return
        val lengthM = pathLengthMeters(state.drawPoints).roundToInt()
        val track = GpxTrack(
            points = state.drawPoints.map { GpxPoint(it.lat, it.lng) },
            lengthM = lengthM,
            durationMin = estimateMinutes(lengthM.toDouble(), state.walkSpeedKmh),
            start = start.toEndpoint(),
            end = end.toEndpoint(),
            markStartAsHome = state.drawMarkStartAsHome,
            source = "drawn",
        )
        runMutation(R.string.map_saved) {
            apiClientProvider.service.commitGpx(GpxCommitRequest(listOf(track)))
        }
    }

    fun parseGpxFile(file: File) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(actionBusy = true, messageRes = null)
            val part = MultipartBody.Part.createFormData(
                "file",
                file.name,
                file.asRequestBody("application/gpx+xml".toMediaType()),
            )
            val response = runCatching { apiClientProvider.service.parseGpx(part) }.getOrNull()
            if (response?.isSuccessful == true) {
                val tracks = response.body()?.tracks.orEmpty()
                val decisions = tracks.map { track ->
                    val startPt = track.points.firstOrNull()
                    val endPt = track.points.lastOrNull()
                    val start = startPt?.let {
                        initialEndpointDecision(LatLng(it.lat, it.lng), _uiState.value.nodes, _uiState.value.mergeRadiusM)
                    } ?: EndpointDecision.NewJunction()
                    val end = endPt?.let {
                        initialEndpointDecision(LatLng(it.lat, it.lng), _uiState.value.nodes, _uiState.value.mergeRadiusM)
                    } ?: EndpointDecision.NewJunction()
                    start to end
                }
                _uiState.value = _uiState.value.copy(
                    actionBusy = false,
                    gpxTracks = tracks,
                    gpxDecisions = decisions,
                    gpxSkip = List(tracks.size) { false },
                    mode = MapMode.Gpx,
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    actionBusy = false,
                    messageRes = R.string.map_gpx_parse_error,
                    isError = true,
                )
            }
        }
    }

    fun updateGpxDecision(trackIndex: Int, isStart: Boolean, decision: EndpointDecision) {
        val decisions = _uiState.value.gpxDecisions.toMutableList()
        if (trackIndex !in decisions.indices) return
        val (start, end) = decisions[trackIndex]
        decisions[trackIndex] = if (isStart) decision to end else start to decision
        _uiState.value = _uiState.value.copy(gpxDecisions = decisions)
    }

    fun toggleGpxSkip(trackIndex: Int, skip: Boolean) {
        val skipList = _uiState.value.gpxSkip.toMutableList()
        if (trackIndex in skipList.indices) {
            skipList[trackIndex] = skip
            _uiState.value = _uiState.value.copy(gpxSkip = skipList)
        }
    }

    fun commitGpxImport() {
        val state = _uiState.value
        val tracks = state.gpxTracks ?: return
        val toCommit = tracks.mapIndexedNotNull { index, preview ->
            if (state.gpxSkip.getOrElse(index) { false }) return@mapIndexedNotNull null
            val (start, end) = state.gpxDecisions.getOrElse(index) {
                EndpointDecision.NewJunction() to EndpointDecision.NewJunction()
            }
            GpxTrack(
                points = preview.points,
                lengthM = preview.lengthM,
                durationMin = preview.durationMin,
                elevation = preview.elevation,
                start = start.toEndpoint(),
                end = end.toEndpoint(),
                source = "gpx",
            )
        }
        if (toCommit.isEmpty()) return
        runMutation(R.string.map_saved) {
            apiClientProvider.service.commitGpx(GpxCommitRequest(toCommit))
        }
    }

    fun cancelGpxImport() {
        _uiState.value = _uiState.value.copy(
            gpxTracks = null,
            gpxDecisions = emptyList(),
            gpxSkip = emptyList(),
            mode = MapMode.View,
        )
    }

    fun nodeCandidates(point: LatLng) =
        findNodeCandidates(_uiState.value.nodes, point, _uiState.value.mergeRadiusM)

    private fun EndpointDecision.toEndpoint(): GpxEndpoint = when (this) {
        is EndpointDecision.Existing -> GpxEndpoint.existing(nodeId)
        is EndpointDecision.NewJunction -> GpxEndpoint.new(part1, part2)
    }

    private fun denyNotAllowed() {
        _uiState.value = _uiState.value.copy(
            messageRes = R.string.map_not_allowed,
            isError = true,
        )
    }

    private fun runMutation(successRes: Int, call: suspend () -> retrofit2.Response<Unit>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(actionBusy = true, messageRes = null)
            val response = runCatching { call() }.getOrNull()
            if (response?.isSuccessful == true) {
                bootstrapLoader.invalidate()
                loadNetwork(forceRefresh = true)
                val prev = _uiState.value
                val leaveEditor = successRes == R.string.map_saved && prev.mode != MapMode.View
                _uiState.value = prev.copy(
                    actionBusy = false,
                    messageRes = successRes,
                    isError = false,
                    renamingNode = false,
                    renamingSegment = false,
                    moveNodeId = null,
                    mode = if (leaveEditor) MapMode.View else prev.mode,
                    drawPoints = if (leaveEditor && prev.mode == MapMode.Draw) emptyList() else prev.drawPoints,
                    drawPhase = DrawPhase.Drawing,
                    drawStartDecision = if (leaveEditor && prev.mode == MapMode.Draw) null else prev.drawStartDecision,
                    drawEndDecision = if (leaveEditor && prev.mode == MapMode.Draw) null else prev.drawEndDecision,
                    drawMarkStartAsHome = if (leaveEditor && prev.mode == MapMode.Draw) false else prev.drawMarkStartAsHome,
                    gpxTracks = if (leaveEditor && prev.mode == MapMode.Gpx) null else prev.gpxTracks,
                    gpxDecisions = if (leaveEditor && prev.mode == MapMode.Gpx) emptyList() else prev.gpxDecisions,
                    gpxSkip = if (leaveEditor && prev.mode == MapMode.Gpx) emptyList() else prev.gpxSkip,
                    editSegmentPoints = if (leaveEditor && prev.mode == MapMode.EditSegment) null else prev.editSegmentPoints,
                    splitTarget = if (leaveEditor && prev.mode == MapMode.SplitSegment) null else prev.splitTarget,
                    splitDecision = if (leaveEditor && prev.mode == MapMode.SplitSegment) null else prev.splitDecision,
                )
                if (successRes == R.string.map_deleted) clearSelection()
            } else {
                val errorRes = if (response?.code() == 403) R.string.map_not_allowed else R.string.map_action_error
                _uiState.value = _uiState.value.copy(
                    actionBusy = false,
                    messageRes = errorRes,
                    isError = true,
                )
            }
        }
    }

    fun refreshProposals() {
        viewModelScope.launch {
            val res = runCatching { apiClientProvider.service.proposals() }.getOrNull()
            if (res?.isSuccessful == true) {
                _uiState.value = _uiState.value.copy(proposals = res.body()?.proposals.orEmpty())
            }
        }
    }

    fun startReportCondition() {
        _uiState.value = _uiState.value.copy(reportingCondition = true, conditionReason = "muddy")
    }

    fun cancelReportCondition() {
        _uiState.value = _uiState.value.copy(reportingCondition = false)
    }

    fun setConditionReason(reason: String) {
        _uiState.value = _uiState.value.copy(conditionReason = reason)
    }

    fun submitConditionReport() {
        val segment = _uiState.value.selectedSegment ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(actionBusy = true)
            val res = runCatching {
                apiClientProvider.service.reportSegmentCondition(
                    ReportConditionRequest(segment.id, _uiState.value.conditionReason),
                )
            }.getOrNull()
            if (res?.isSuccessful == true) {
                bootstrapLoader.invalidate()
                loadNetwork(forceRefresh = true)
                _uiState.value = _uiState.value.copy(
                    actionBusy = false,
                    reportingCondition = false,
                    messageRes = R.string.map_condition_reported,
                    isError = false,
                )
            } else {
                _uiState.value = _uiState.value.copy(actionBusy = false, messageRes = R.string.common_error, isError = true)
            }
        }
    }

    fun acceptProposal(proposalId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(actionBusy = true)
            val res = runCatching {
                apiClientProvider.service.acceptProposal(com.routy.app.logic.api.ProposalActionRequest(proposalId))
            }.getOrNull()
            if (res?.isSuccessful == true) {
                bootstrapLoader.invalidate()
                loadNetwork(forceRefresh = true)
                refreshProposals()
                _uiState.value = _uiState.value.copy(actionBusy = false, messageRes = R.string.map_proposal_accepted, isError = false)
            } else {
                _uiState.value = _uiState.value.copy(actionBusy = false, messageRes = R.string.common_error, isError = true)
            }
        }
    }

    fun dismissProposal(proposalId: Int) {
        viewModelScope.launch {
            val res = runCatching {
                apiClientProvider.service.dismissProposal(com.routy.app.logic.api.ProposalActionRequest(proposalId))
            }.getOrNull()
            if (res?.isSuccessful == true) refreshProposals()
        }
    }

    fun conditionsForSegment(segmentId: Int): List<SegmentConditionDto> =
        _uiState.value.segmentConditions.filter { it.segmentId == segmentId }

    private fun loadGpxConfig() {
        viewModelScope.launch {
            val config = runCatching { apiClientProvider.service.gpxConfig() }.getOrNull()
                ?.takeIf { it.isSuccessful }?.body()
            if (config != null) {
                _uiState.value = _uiState.value.copy(
                    mergeRadiusM = config.mergeRadiusM,
                    walkSpeedKmh = config.walkSpeedKmh,
                )
            }
        }
    }

    private fun loadNetwork(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            if (!forceRefresh) {
                networkCache.loadBootstrap()?.let { cached ->
                    applyNetwork(cached.user, cached.nodes, cached.segments, cached.segmentConditions, offline = false)
                }
            }

            when (val result = bootstrapLoader.load()) {
                is BootstrapResult.Fresh -> applyNetwork(result.body.user, result.body.nodes, result.body.segments, result.body.segmentConditions, offline = false)
                is BootstrapResult.NotModified -> applyNetwork(result.cached.user, result.cached.nodes, result.cached.segments, result.cached.segmentConditions, offline = false)
                is BootstrapResult.CachedOnly -> applyNetwork(result.cached.user, result.cached.nodes, result.cached.segments, result.cached.segmentConditions, offline = true)
                BootstrapResult.Unauthorized, BootstrapResult.Failed -> {
                    if (_uiState.value.nodes.isEmpty()) {
                        _uiState.value = _uiState.value.copy(loading = false, loadFailed = true)
                    }
                }
            }
            refreshProposals()
        }
    }

    private fun applyNetwork(
        user: SessionUser,
        nodes: List<NodeDto>,
        segments: List<SegmentDto>,
        segmentConditions: List<SegmentConditionDto>,
        offline: Boolean,
    ) {
        val prev = _uiState.value
        _uiState.value = prev.copy(
            loading = false,
            loadFailed = false,
            offlineCached = offline,
            user = user,
            nodes = nodes,
            segments = segments.filter { it.isCanonical() },
            segmentConditions = segmentConditions,
            selectedNode = prev.selectedNode?.let { sel -> nodes.find { it.id == sel.id } },
            selectedSegment = prev.selectedSegment?.let { sel -> segments.find { it.id == sel.id && it.isCanonical() } },
        )
    }
}
