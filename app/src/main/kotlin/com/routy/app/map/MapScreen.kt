package com.routy.app.map

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.routy.app.R
import com.routy.app.RoutyApplication
import com.routy.app.logic.api.GeoPoint
import com.routy.app.logic.api.NodeDto
import com.routy.app.logic.api.SegmentDto
import com.routy.app.logic.geo.LatLng
import com.routy.app.logic.geo.pathLengthMeters
import com.routy.app.logic.recording.EndpointDecision
import com.routy.app.logic.recording.NodeCandidate
import java.io.File
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MapScreen(onStartRecording: () -> Unit, modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as RoutyApplication
    val viewModel: MapViewModel = viewModel(
        factory = viewModelFactory {
            initializer { MapViewModel(app.networkCache, app.bootstrapLoader, app.apiClientProvider) }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    var mapStyle by remember { mutableStateOf(BaseMapStyle.STREETS) }

    val gpxPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val file = File(app.cacheDir, "import.gpx")
        app.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        viewModel.parseGpxFile(file)
    }

    LaunchedEffect(uiState.messageRes) {
        if (uiState.messageRes != null) {
            delay(3500)
            viewModel.clearMessage()
        }
    }

    if (uiState.loading && uiState.nodes.isEmpty() && !uiState.loadFailed) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    if (uiState.loadFailed && uiState.nodes.isEmpty()) {
        Column(
            modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.map_load_error))
            TextButton(onClick = viewModel::refresh) { Text(stringResource(R.string.stats_retry)) }
        }
        return
    }

    val overlayLine = when (uiState.mode) {
        MapMode.Draw -> uiState.drawPoints.map { GeoPoint(it.lat, it.lng) }
        MapMode.EditSegment -> uiState.editSegmentPoints.orEmpty()
        else -> emptyList()
    }

    Box(modifier = modifier.fillMaxSize()) {
        RoutyMapView(
            style = mapStyle,
            nodes = uiState.nodes,
            segments = uiState.segments,
            routeGeometry = emptyList(),
            stations = emptyList(),
            myLocation = null,
            fitKey = uiState.nodes.size,
            fitToRouteOnly = false,
            emphasizeNetworkSegments = true,
            selectedNodeId = uiState.selectedNode?.id ?: uiState.moveNodeId,
            selectedSegmentId = uiState.selectedSegment?.id,
            moveNodeId = uiState.moveNodeId,
            overlayLine = overlayLine,
            onMapClick = viewModel::onMapClick,
            modifier = Modifier.fillMaxSize(),
        )

        MapStyleSwitcher(
            selected = mapStyle,
            onSelect = { mapStyle = it },
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
        )

        if (uiState.actionBusy) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            uiState.messageRes?.let { res ->
                Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)) {
                    Text(
                        stringResource(res),
                        modifier = Modifier.padding(8.dp),
                        color = if (uiState.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            when (uiState.mode) {
                MapMode.View -> {
                    uiState.selectedNode?.let { MapNodePanel(it, uiState, viewModel) }
                    uiState.selectedSegment?.let { MapSegmentPanel(it, uiState, viewModel) }
                    MapViewToolbar(uiState, onStartRecording, { viewModel.setMode(MapMode.Draw) }, { gpxPicker.launch("*/*") })
                }
                MapMode.Draw -> MapDrawPanel(uiState, viewModel)
                MapMode.Gpx -> MapGpxPanel(uiState, viewModel)
                MapMode.EditSegment -> MapEditShapePanel(viewModel)
                MapMode.SplitSegment -> MapSplitPanel(uiState, viewModel)
            }

            Text(mapHint(uiState), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}

@Composable
private fun mapHint(state: MapUiState): String = when (state.mode) {
    MapMode.View -> if (state.moveNodeId != null) stringResource(R.string.map_hint_move_tap) else stringResource(R.string.map_tap_hint)
    MapMode.Draw -> stringResource(R.string.map_hint_draw)
    MapMode.Gpx -> stringResource(R.string.map_hint_gpx)
    MapMode.EditSegment -> stringResource(R.string.map_hint_edit_shape)
    MapMode.SplitSegment -> stringResource(R.string.map_hint_split)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MapViewToolbar(state: MapUiState, onStartRecording: () -> Unit, onDraw: () -> Unit, onGpx: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f), shape = MaterialTheme.shapes.medium) {
        FlowRow(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AssistChip(onClick = {}, enabled = false, label = { Text(stringResource(R.string.map_nodes_count, state.nodes.size), style = MaterialTheme.typography.labelSmall) })
            CompactButton(onDraw) { Text(stringResource(R.string.map_draw_path), style = MaterialTheme.typography.labelMedium) }
            CompactOutlinedButton(onGpx) { Text(stringResource(R.string.map_import_gpx), style = MaterialTheme.typography.labelMedium) }
            CompactButton(onStartRecording) { Text(stringResource(R.string.map_record_track), style = MaterialTheme.typography.labelMedium) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MapNodePanel(node: NodeDto, state: MapUiState, viewModel: MapViewModel) {
    val canEdit = viewModel.canEditNode(node)
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(node.name ?: "#${node.id}", style = MaterialTheme.typography.titleSmall)
            if (node.isHome) Text(stringResource(R.string.map_node_home), style = MaterialTheme.typography.labelSmall)
            if (canEdit) {
                if (state.renamingNode) {
                    OutlinedTextField(state.renamePart1, viewModel::updateRenamePart1, label = { Text(stringResource(R.string.record_name_part1)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(state.renamePart2, viewModel::updateRenamePart2, label = { Text(stringResource(R.string.record_name_part2)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    CompactButton(viewModel::saveRenameNode) { Text(stringResource(R.string.map_rename)) }
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CompactOutlinedButton(viewModel::startRenameNode) { Text(stringResource(R.string.map_rename)) }
                        if (!node.isHome) CompactOutlinedButton(viewModel::setHomeNode) { Text(stringResource(R.string.map_set_home)) }
                        CompactOutlinedButton(viewModel::toggleMoveNode) {
                            Text(if (state.moveNodeId == node.id) stringResource(R.string.map_move_active) else stringResource(R.string.map_move))
                        }
                        CompactOutlinedButton(viewModel::deleteSelectedNode) { Text(stringResource(R.string.map_delete)) }
                    }
                }
            } else if (!node.isHome) {
                CompactOutlinedButton(viewModel::setHomeNode) { Text(stringResource(R.string.map_set_home)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MapSegmentPanel(segment: SegmentDto, state: MapUiState, viewModel: MapViewModel) {
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(segment.name ?: "#${segment.id}", style = MaterialTheme.typography.titleSmall)
            if (!viewModel.canEditSegment(segment)) return@Column
            if (state.renamingSegment) {
                OutlinedTextField(state.renameSegmentName, viewModel::updateRenameSegmentName, label = { Text(stringResource(R.string.map_segment_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                CompactButton(viewModel::saveRenameSegment) { Text(stringResource(R.string.map_rename)) }
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CompactOutlinedButton(viewModel::startRenameSegment) { Text(stringResource(R.string.map_rename)) }
                    CompactOutlinedButton({ viewModel.lockSegment(7) }) { Text(stringResource(R.string.map_lock)) }
                    if (segment.lockedUntil != null) CompactOutlinedButton({ viewModel.lockSegment(null) }) { Text(stringResource(R.string.map_unlock)) }
                    CompactOutlinedButton(viewModel::startEditSegmentShape) { Text(stringResource(R.string.map_edit_shape)) }
                    CompactOutlinedButton(viewModel::startSplitSegment) { Text(stringResource(R.string.map_split)) }
                    CompactOutlinedButton(viewModel::deleteSelectedSegment) { Text(stringResource(R.string.map_delete)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MapDrawPanel(state: MapUiState, viewModel: MapViewModel) {
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(10.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (state.drawPhase == DrawPhase.Drawing) {
                Text(stringResource(R.string.map_draw_points, state.drawPoints.size), style = MaterialTheme.typography.labelSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CompactOutlinedButton(viewModel::undoDrawPoint) { Text(stringResource(R.string.map_undo)) }
                    CompactOutlinedButton(viewModel::clearDrawPoints) { Text(stringResource(R.string.map_clear)) }
                    CompactButton(viewModel::finishDraw, enabled = state.drawPoints.size >= 2) { Text(stringResource(R.string.map_finish)) }
                    CompactOutlinedButton({ viewModel.setMode(MapMode.View) }) { Text(stringResource(R.string.common_close)) }
                }
            } else {
                val start = state.drawStartDecision ?: return@Column
                val end = state.drawEndDecision ?: return@Column
                EndpointBlock(stringResource(R.string.map_start), viewModel.nodeCandidates(state.drawPoints.first()), start, viewModel::updateDrawStartDecision)
                EndpointBlock(stringResource(R.string.map_end), viewModel.nodeCandidates(state.drawPoints.last()), end, viewModel::updateDrawEndDecision)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(state.drawMarkStartAsHome, viewModel::setDrawMarkStartAsHome)
                    Text(stringResource(R.string.record_mark_as_home), style = MaterialTheme.typography.labelSmall)
                }
                CompactButton(viewModel::saveDrawPath) { Text(stringResource(R.string.map_save)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MapGpxPanel(state: MapUiState, viewModel: MapViewModel) {
    val tracks = state.gpxTracks ?: return
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(10.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tracks.forEachIndexed { index, track ->
                val (start, end) = state.gpxDecisions.getOrElse(index) { EndpointDecision.NewJunction() to EndpointDecision.NewJunction() }
                val startPt = track.points.firstOrNull()?.let { LatLng(it.lat, it.lng) }
                val endPt = track.points.lastOrNull()?.let { LatLng(it.lat, it.lng) }
                Text(track.name ?: stringResource(R.string.map_gpx_track, index + 1), style = MaterialTheme.typography.titleSmall)
                if (startPt != null) EndpointBlock(stringResource(R.string.map_start), viewModel.nodeCandidates(startPt), start) { viewModel.updateGpxDecision(index, true, it) }
                if (endPt != null) EndpointBlock(stringResource(R.string.map_end), viewModel.nodeCandidates(endPt), end) { viewModel.updateGpxDecision(index, false, it) }
            }
            CompactButton(viewModel::commitGpxImport) { Text(stringResource(R.string.map_save)) }
            CompactOutlinedButton(viewModel::cancelGpxImport) { Text(stringResource(R.string.common_close)) }
        }
    }
}

@Composable
private fun MapEditShapePanel(viewModel: MapViewModel) {
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f), shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CompactButton(viewModel::finishEditSegmentShape) { Text(stringResource(R.string.map_save)) }
            CompactOutlinedButton(viewModel::cancelEditSegmentShape) { Text(stringResource(R.string.common_close)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MapSplitPanel(state: MapUiState, viewModel: MapViewModel) {
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            state.splitTarget?.let { target ->
                val decision = state.splitDecision ?: EndpointDecision.NewJunction()
                EndpointBlock(stringResource(R.string.map_split_junction), viewModel.nodeCandidates(target), decision, viewModel::updateSplitDecision)
                CompactButton(viewModel::confirmSplit) { Text(stringResource(R.string.map_split_confirm)) }
            }
            CompactOutlinedButton(viewModel::cancelSplit) { Text(stringResource(R.string.common_close)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EndpointBlock(label: String, candidates: List<NodeCandidate>, decision: EndpointDecision, onDecisionChange: (EndpointDecision) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(decision is EndpointDecision.Existing, onClick = { candidates.firstOrNull()?.let { onDecisionChange(EndpointDecision.Existing(it.id)) } }, enabled = candidates.isNotEmpty(), label = { Text(stringResource(R.string.record_use_existing), style = MaterialTheme.typography.labelSmall) })
            FilterChip(decision is EndpointDecision.NewJunction, onClick = { onDecisionChange(EndpointDecision.NewJunction()) }, label = { Text(stringResource(R.string.record_create_new), style = MaterialTheme.typography.labelSmall) })
        }
        if (decision is EndpointDecision.NewJunction) {
            OutlinedTextField(decision.part1, { onDecisionChange(decision.copy(part1 = it)) }, placeholder = { Text(stringResource(R.string.record_name_part1)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(decision.part2, { onDecisionChange(decision.copy(part2 = it)) }, placeholder = { Text(stringResource(R.string.record_name_part2)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
    }
}

private val CompactPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)

@Composable
private fun CompactButton(onClick: () -> Unit, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    Button(onClick, enabled = enabled, contentPadding = CompactPadding, content = content)
}

@Composable
private fun CompactOutlinedButton(onClick: () -> Unit, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    OutlinedButton(onClick, enabled = enabled, contentPadding = CompactPadding, content = content)
}
