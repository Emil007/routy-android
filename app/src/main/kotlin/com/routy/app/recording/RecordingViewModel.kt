package com.routy.app.recording

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routy.app.R
import com.routy.app.core.StatsInvalidation
import com.routy.app.core.BootstrapLoader
import com.routy.app.core.BootstrapResult
import com.routy.app.core.network.ApiClientProvider
import com.routy.app.core.storage.RecordingConfirmStore
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
import com.routy.app.logic.recording.RecordingConfirmDraft
import com.routy.app.logic.recording.RecordingPoint
import com.routy.app.logic.recording.matchesTrack
import com.routy.app.logic.recording.findNodeCandidates
import com.routy.app.logic.recording.initialEndpointDecision
import com.routy.app.logic.recording.recordingDurationMin
import java.io.IOException
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RecordingUiState(
    val loadingConfig: Boolean = true,
    val offlineCached: Boolean = false,
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
class RecordingViewModel(
    private val apiClientProvider: ApiClientProvider,
    private val gpxCommitScheduler: GpxCommitScheduler,
    private val confirmStore: RecordingConfirmStore,
    private val bootstrapLoader: BootstrapLoader,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RecordingUiState())
    val uiState: StateFlow<RecordingUiState> = _uiState.asStateFlow()

    // M16: Punkte, die ankamen, solange die Knoten noch nicht geladen waren — werden
    // nach dem Laden neu gematcht (statt ein vorschnelles NewJunction festzuschreiben).
    private var pendingPointsWhileLoading: List<RecordingPoint>? = null
    // M16: hat der Nutzer Start/Ende selbst entschieden, wird NICHT überschrieben.
    private var decisionsTouchedByUser = false

    init {
        loadConfig()
    }

    private fun loadConfig() {
        viewModelScope.launch {
            val offline = bootstrapLoader.load() is BootstrapResult.CachedOnly
            val service = apiClientProvider.service
            val nodesResponse = runCatching { service.nodes() }.getOrNull()
            val configResponse = runCatching { service.gpxConfig() }.getOrNull()
            val nodes = nodesResponse?.takeIf { it.isSuccessful }?.body()?.nodes.orEmpty()
            val config = configResponse?.takeIf { it.isSuccessful }?.body()
            _uiState.value = _uiState.value.copy(
                loadingConfig = false,
                offlineCached = offline,
                nodes = nodes,
                mergeRadiusM = config?.mergeRadiusM ?: _uiState.value.mergeRadiusM,
                walkSpeedKmh = config?.walkSpeedKmh ?: _uiState.value.walkSpeedKmh,
            )
            // M16: wartende Punkte jetzt mit voller Knotenliste neu matchen.
            recomputeEndpointsAfterConfig()
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
        if (points.size < 2) return
        // M16: Endpunkte erst festlegen, wenn die Knoten geladen sind — sonst wird
        // aus einem leeren Match ein vorschnelles NewJunction gespeichert (F22).
        if (_uiState.value.loadingConfig) {
            pendingPointsWhileLoading = points
            return
        }
        applyEndpointDecisions(points)
    }

    private fun recomputeEndpointsAfterConfig() {
        val pending = pendingPointsWhileLoading ?: return
        pendingPointsWhileLoading = null
        if (decisionsTouchedByUser) {
            // Nutzer hat schon entschieden — nur die Punkte nachtragen, Entscheidungen behalten.
            if (pending.size >= 2) {
                _uiState.value = _uiState.value.copy(points = pending)
                persistConfirmDraft()
            }
            return
        }
        applyEndpointDecisions(pending)
    }

    private fun applyEndpointDecisions(points: List<RecordingPoint>) {
        val state = _uiState.value
        val draft = confirmStore.load()?.takeIf { it.matchesTrack(points) }
        val start = points.first()
        val end = points.last()
        _uiState.value = state.copy(
            points = points,
            startDecision = draft?.startDecision
                ?: initialEndpointDecision(LatLng(start.lat, start.lng), state.nodes, state.mergeRadiusM),
            endDecision = draft?.endDecision
                ?: initialEndpointDecision(LatLng(end.lat, end.lng), state.nodes, state.mergeRadiusM),
            markStartAsHome = draft?.markStartAsHome ?: false,
        )
        persistConfirmDraft()
    }

    private fun persistConfirmDraft() {
        val state = _uiState.value
        val start = state.startDecision ?: return
        val end = state.endDecision ?: return
        if (state.points.size < 2) return
        confirmStore.save(
            RecordingConfirmDraft(
                points = state.points,
                startDecision = start,
                endDecision = end,
                markStartAsHome = state.markStartAsHome,
            ),
        )
    }

    fun setStartDecision(decision: EndpointDecision) {
        decisionsTouchedByUser = true
        _uiState.value = _uiState.value.copy(startDecision = decision)
        persistConfirmDraft()
    }

    fun setEndDecision(decision: EndpointDecision) {
        decisionsTouchedByUser = true
        _uiState.value = _uiState.value.copy(endDecision = decision)
        persistConfirmDraft()
    }

    fun setMarkStartAsHome(value: Boolean) {
        _uiState.value = _uiState.value.copy(markStartAsHome = value)
        persistConfirmDraft()
    }

    /** Resets to a clean slate — called after a successful save, or when discarding from the confirm step. */
    fun reset() {
        confirmStore.clear()
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
                // M17: Dauer aus den Zeitstempeln (gelaufene Zeit), nicht aus der
                // Solltempo-Schätzung — eine langsame/pausierte Runde wird korrekt
                // gemeldet. Schätzung nur als Fallback ohne Stempel.
                durationMin = recordingDurationMin(state.points, lengthM.toDouble(), state.walkSpeedKmh),
                elevation = null,
                start = start.toGpxEndpoint(),
                end = end.toGpxEndpoint(),
                markStartAsHome = state.markStartAsHome,
                source = "gpx",
            )
            val request = GpxCommitRequest(tracks = listOf(track))
            val response = try {
                apiClientProvider.service.commitGpx(request)
            } catch (_: IOException) {
                gpxCommitScheduler.enqueue(request)
                confirmStore.clear()
                _uiState.value = _uiState.value.copy(
                    saving = false,
                    isError = false,
                    messageRes = R.string.record_queued_for_upload,
                    saved = true,
                )
                return@launch
            }
            if (response.isSuccessful) {
                confirmStore.clear()
                StatsInvalidation.bump()
                _uiState.value = _uiState.value.copy(saving = false, isError = false, messageRes = R.string.record_saved, saved = true)
            } else if (response.code() in 500..599) {
                gpxCommitScheduler.enqueue(request)
                confirmStore.clear()
                _uiState.value = _uiState.value.copy(
                    saving = false,
                    isError = false,
                    messageRes = R.string.record_queued_for_upload,
                    saved = true,
                )
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
