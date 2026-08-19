package com.routy.app.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routy.app.core.BootstrapLoader
import com.routy.app.core.BootstrapResult
import com.routy.app.core.storage.NetworkCache
import com.routy.app.logic.api.NodeDto
import com.routy.app.logic.api.SegmentDto
import com.routy.app.logic.geo.LatLng
import com.routy.app.logic.geo.haversineMeters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val NODE_TAP_RADIUS_M = 45.0

data class MapUiState(
    val loading: Boolean = true,
    val offlineCached: Boolean = false,
    val nodes: List<NodeDto> = emptyList(),
    val segments: List<SegmentDto> = emptyList(),
    val selectedNode: NodeDto? = null,
)

class MapViewModel(
    private val networkCache: NetworkCache,
    private val bootstrapLoader: BootstrapLoader,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    init {
        loadNetwork()
    }

    fun refresh() {
        bootstrapLoader.invalidate()
        loadNetwork(forceRefresh = true)
    }

    fun onMapClick(lat: Double, lng: Double) {
        val point = LatLng(lat, lng)
        val nearest = _uiState.value.nodes
            .map { node -> node to haversineMeters(point, LatLng(node.lat, node.lng)) }
            .minByOrNull { it.second }
        val selected = nearest?.takeIf { it.second <= NODE_TAP_RADIUS_M }?.first
        _uiState.value = _uiState.value.copy(selectedNode = selected)
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedNode = null)
    }

    private fun loadNetwork(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            if (!forceRefresh) {
                networkCache.loadBootstrap()?.let { cached ->
                    applyNetwork(cached.nodes, cached.segments, offline = true)
                }
            }

            when (val result = bootstrapLoader.load()) {
                is BootstrapResult.Fresh -> applyNetwork(result.body.nodes, result.body.segments, offline = false)
                is BootstrapResult.NotModified -> applyNetwork(result.cached.nodes, result.cached.segments, offline = false)
                is BootstrapResult.CachedOnly -> applyNetwork(result.cached.nodes, result.cached.segments, offline = true)
                BootstrapResult.Unauthorized, BootstrapResult.Failed -> {
                    if (_uiState.value.nodes.isEmpty()) {
                        _uiState.value = _uiState.value.copy(loading = false)
                    }
                }
            }
        }
    }

    private fun applyNetwork(nodes: List<NodeDto>, segments: List<SegmentDto>, offline: Boolean) {
        _uiState.value = _uiState.value.copy(
            loading = false,
            offlineCached = offline,
            nodes = nodes,
            segments = segments,
            selectedNode = _uiState.value.selectedNode?.let { prev ->
                nodes.find { it.id == prev.id }
            },
        )
    }
}
