package com.routy.app.recording

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routy.app.R
import com.routy.app.core.network.ApiClientProvider
import com.routy.app.logic.api.GpxCommitRequest
import com.routy.app.logic.api.GpxEndpoint
import com.routy.app.logic.api.GpxPoint
import com.routy.app.logic.api.GpxTrack
import com.routy.app.logic.api.NodeDto
import com.routy.app.logic.geo.LatLng
import com.routy.app.logic.geo.estimateMinutes
import com.routy.app.logic.geo.pathLengthMeters
import com.routy.app.logic.recording.EndpointDecision
import com.routy.app.logic.recording.NodeCandidate
import com.routy.app.logic.recording.RecordingPoint
import com.routy.app.logic.recording.findNodeCandidates
import com.routy.app.logic.recording.initialEndpointDecision
import java.io.IOException
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RecordingUiState(
    val loadingConfig: Boolean = true,
    val nodes: List<NodeDto> = emptyList(),
    val mergeRadiusM: Double = 50.0,
    val walkSpeedKmh: Double = 5.0,

    /** Set once RecordingForegroundService.finish() hands back the recorded track. */
    val points: List<RecordingPoint> = emptyList(),
    val startDecision: EndpointDecision? = null,
    val endDecision: EndpointDecision? = null,
    val markStartAsHome: Boolean = false,

    val saving: Boolean = false,
    val messageRes: Int? = null,
    val isError: Boolean = false,
    val saved: Boolean = false,
)

/**
 * Owns the confirm-step wizard (candidate-junction matching, the start/end endpoint decision,
 * markStartAsHome, and the final POST /api/gpx/commit) — ports RecordTrackWizard.tsx's `save()`
 * and its endpoint-decision state exactly, using :logic's already-tested findNodeCandidates /
 * initialEndpointDecision rather than reimplementing that matching here. Does NOT own the live
 * recording itself — RecordingForegroundService is the source of truth for "is GPS running right
 * now" (it has to survive this ViewModel's owner being destroyed if the app backgrounds), so
 * RecordingScreen drives the service directly and only calls into this ViewModel once a track is
 * ready to be reviewed and saved.
 */
class RecordingViewModel(private val apiClientProvider: ApiClientProvider) : ViewModel() {
    private val _uiState = MutableStateFlow(RecordingUiState())
    val uiState: StateFlow<RecordingUiState> = _uiState.asStateFlow()

    init {
        loadConfig()
    }

    private fun loadConfig() {
        viewModelScope.launch {
            val service = apiClientProvider.service
            val nodesResponse = runCatching { service.nodes() }.getOrNull()
            val configResponse = runCatching { service.gpxConfig() }.getOrNull()
            val nodes = nodesResponse?.takeIf { it.isSuccessful }?.body()?.nodes.orEmpty()
            val config = configResponse?.takeIf { it.isSuccessful }?.body()
            _uiState.value = _uiState.value.copy(
                loadingConfig = false,
                nodes = nodes,
                mergeRadiusM = config?.mergeRadiusM ?: _uiState.value.mergeRadiusM,
                walkSpeedKmh = config?.walkSpeedKmh ?: _uiState.value.walkSpeedKmh,
            )
        }
    }

    fun startCandidates(): List<NodeCandidate> {
        val state = _uiState.value
        val start = state.points.firstOrNull() ?: return emptyList()
        return findNodeCandidates(state.nodes, LatLng(start.lat, start.lng), state.mergeRadiusM)
    }

    fun endCandidates(): List<NodeCandidate> {
        val state = _uiState.value
        val end = state.points.lastOrNull() ?: return emptyList()
        return findNodeCandidates(state.nodes, LatLng(end.lat, end.lng), state.mergeRadiusM)
    }

    /** Called once RecordingForegroundService.finish() returns the recorded points. */
    fun setRecordedPoints(points: List<RecordingPoint>) {
        val state = _uiState.value
        val start = points.first()
        val end = points.last()
        _uiState.value = state.copy(
            points = points,
            startDecision = initialEndpointDecision(LatLng(start.lat, start.lng), state.nodes, state.mergeRadiusM),
            endDecision = initialEndpointDecision(LatLng(end.lat, end.lng), state.nodes, state.mergeRadiusM),
        )
    }

    fun setStartDecision(decision: EndpointDecision) {
        _uiState.value = _uiState.value.copy(startDecision = decision)
    }

    fun setEndDecision(decision: EndpointDecision) {
        _uiState.value = _uiState.value.copy(endDecision = decision)
    }

    fun setMarkStartAsHome(value: Boolean) {
        _uiState.value = _uiState.value.copy(markStartAsHome = value)
    }

    /** Resets to a clean slate — called after a successful save, or when discarding from the confirm step. */
    fun reset() {
        _uiState.value = _uiState.value.copy(
            points = emptyList(),
            startDecision = null,
            endDecision = null,
            markStartAsHome = false,
            messageRes = null,
            isError = false,
            saved = false,
        )
    }

    fun save() {
        val state = _uiState.value
        val start = state.startDecision ?: return
        val end = state.endDecision ?: return
        if (state.points.size < 2) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(saving = true, messageRes = null)
            val lengthM = pathLengthMeters(state.points.map { LatLng(it.lat, it.lng) }).roundToInt()
            val track = GpxTrack(
                points = state.points.map { GpxPoint(it.lat, it.lng, it.ele) },
                lengthM = lengthM,
                durationMin = estimateMinutes(lengthM.toDouble(), state.walkSpeedKmh),
                elevation = null,
                start = start.toGpxEndpoint(),
                end = end.toGpxEndpoint(),
                markStartAsHome = state.markStartAsHome,
                source = "gpx",
            )
            val response = try {
                apiClientProvider.service.commitGpx(GpxCommitRequest(tracks = listOf(track)))
            } catch (_: IOException) {
                _uiState.value = _uiState.value.copy(saving = false, isError = true, messageRes = R.string.common_error)
                return@launch
            }
            if (response.isSuccessful) {
                _uiState.value = _uiState.value.copy(saving = false, isError = false, messageRes = R.string.record_saved, saved = true)
            } else {
                _uiState.value = _uiState.value.copy(saving = false, isError = true, messageRes = R.string.common_error)
            }
        }
    }

    private fun EndpointDecision.toGpxEndpoint(): GpxEndpoint = when (this) {
        is EndpointDecision.Existing -> GpxEndpoint.existing(nodeId)
        is EndpointDecision.NewJunction -> GpxEndpoint.new(part1, part2)
    }
}
