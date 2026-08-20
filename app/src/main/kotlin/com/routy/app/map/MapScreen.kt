package com.routy.app.map

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.routy.app.R
import com.routy.app.RoutyApplication
import com.routy.app.ui.OfflineBanner
import com.routy.app.logic.api.GeoPoint
import com.routy.app.logic.api.NodeDto
import com.routy.app.logic.api.SegmentDto
import com.routy.app.logic.api.isLocked
import com.routy.app.logic.geo.LatLng
import com.routy.app.logic.geo.pathLengthMeters
import com.routy.app.logic.recording.EndpointDecision
import com.routy.app.logic.recording.NodeCandidate
import java.io.File
import kotlinx.coroutines.delay
import java.text.Collator
import java.util.Locale

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
    var waymarkedOverlay by remember { mutableStateOf(false) }
    var showExtras by remember { mutableStateOf(false) }

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
            waymarkedOverlay = waymarkedOverlay,
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
            editVertices = if (uiState.mode == MapMode.EditSegment) uiState.editSegmentPoints else null,
            selectedEditVertexIndex = uiState.selectedEditVertexIndex,
            onMapClick = viewModel::onMapClick,
            modifier = Modifier.fillMaxSize(),
        )

        MapStyleSwitcher(
            selected = mapStyle,
            onSelect = { mapStyle = it },
            waymarkedOverlay = waymarkedOverlay,
            onWaymarkedOverlayChange = { waymarkedOverlay = it },
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
        )

        if (uiState.offlineCached) {
            OfflineBanner(modifier = Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 56.dp))
        }

        if (uiState.mode == MapMode.View) {
            IconButton(
                onClick = { showExtras = !showExtras },
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
            ) {
                Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.map_more_options))
            }
        }

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
                    MapExtrasContent(uiState, viewModel, visible = showExtras)
                }
                MapMode.Draw -> MapDrawPanel(uiState, viewModel)
                MapMode.Gpx -> MapGpxPanel(uiState, viewModel)
                MapMode.EditSegment -> MapEditShapePanel(uiState, viewModel)
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
    MapMode.EditSegment -> when {
        state.moveEditVertexIndex != null -> stringResource(R.string.map_hint_move_vertex)
        else -> stringResource(R.string.map_hint_edit_shape)
    }
    MapMode.SplitSegment -> stringResource(R.string.map_hint_split)
}

@Composable
private fun MapViewToolbar(state: MapUiState, onStartRecording: () -> Unit, onDraw: () -> Unit, onGpx: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f), shape = MaterialTheme.shapes.medium) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.map_nodes_count, state.nodes.size),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
            CompactButton(onDraw, modifier = Modifier.weight(1f), contentPadding = ToolbarPadding) {
                Text(
                    stringResource(R.string.map_toolbar_draw),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            CompactOutlinedButton(onGpx, modifier = Modifier.weight(1f), contentPadding = ToolbarPadding) {
                Text(
                    stringResource(R.string.map_toolbar_gpx),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            CompactButton(onStartRecording, modifier = Modifier.weight(1f), contentPadding = ToolbarPadding) {
                Text(
                    stringResource(R.string.map_toolbar_record),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MapNodePanel(node: NodeDto, state: MapUiState, viewModel: MapViewModel) {
    val canEdit = viewModel.canEditNode(node)
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(node.name ?: "#${node.id}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                CompactOutlinedButton(viewModel::clearSelection) { Text(stringResource(R.string.common_close), style = MaterialTheme.typography.labelSmall) }
            }
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

@Composable
private fun MapExtrasContent(state: MapUiState, viewModel: MapViewModel, visible: Boolean) {
    AnimatedVisibility(visible = visible) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (state.proposals.isNotEmpty()) {
                ProposalsPanel(state, viewModel)
            }
            if (state.lockProposals.isNotEmpty()) {
                LockProposalsPanel(state, viewModel)
            }
            SegmentsTablePanel(state, viewModel)
            TrashPanel(state, viewModel)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProposalsPanel(state: MapUiState, viewModel: MapViewModel) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.94f), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.map_proposals_title, state.proposals.size), style = MaterialTheme.typography.titleSmall)
            if (state.proposals.isEmpty()) {
                Text(stringResource(R.string.map_proposals_empty), style = MaterialTheme.typography.bodySmall)
            } else {
                state.proposals.forEach { proposal ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            proposal.segmentName ?: stringResource(R.string.map_proposal_segment, proposal.segmentId),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (viewModel.canEditProposal(proposal)) {
                            CompactOutlinedButton({ viewModel.acceptProposal(proposal.id) }) {
                                Text(stringResource(R.string.map_proposal_accept), style = MaterialTheme.typography.labelSmall)
                            }
                            CompactOutlinedButton({ viewModel.dismissProposal(proposal.id) }) {
                                Text(stringResource(R.string.map_proposal_dismiss), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LockProposalsPanel(state: MapUiState, viewModel: MapViewModel) {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.94f), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.map_lock_proposals_title, state.lockProposals.size), style = MaterialTheme.typography.titleSmall)
            state.lockProposals.forEach { proposal ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            proposal.segmentName ?: stringResource(R.string.map_proposal_segment, proposal.segmentId),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            stringResource(R.string.map_lock_proposal_days, proposal.days),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (viewModel.canEditLockProposal(proposal)) {
                        CompactOutlinedButton({ viewModel.approveLockProposal(proposal.id) }) {
                            Text(stringResource(R.string.map_lock_proposal_approve), style = MaterialTheme.typography.labelSmall)
                        }
                        CompactOutlinedButton({ viewModel.dismissLockProposal(proposal.id) }) {
                            Text(stringResource(R.string.map_proposal_dismiss), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MapSegmentPanel(segment: SegmentDto, state: MapUiState, viewModel: MapViewModel) {
    val conditions = viewModel.conditionsForSegment(segment.id)
    var showRestrictDialog by remember { mutableStateOf(false) }
    var restrictScope by remember { mutableStateOf("personal") }
    var restrictDays by remember { mutableStateOf("7") }
    var restrictReason by remember { mutableStateOf("muddy") }
    val personalAvoided = viewModel.isPersonallyAvoided(segment.id)
    val locked = segment.isLocked()
    val canEdit = viewModel.canEditSegment(segment)

    if (showRestrictDialog) {
        AlertDialog(
            onDismissRequest = { showRestrictDialog = false },
            title = { Text(stringResource(R.string.map_restrict_button)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RestrictScopePicker(restrictScope, canEdit, onSelect = { restrictScope = it })
                    ConditionReasonPicker(restrictReason, onSelect = { restrictReason = it })
                    OutlinedTextField(
                        value = restrictDays,
                        onValueChange = { restrictDays = it.filter { ch -> ch.isDigit() } },
                        label = { Text(stringResource(R.string.map_lock_days)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showRestrictDialog = false
                    val days = restrictDays.toIntOrNull()?.coerceAtLeast(1) ?: 7
                    viewModel.restrictSegment(restrictScope, restrictReason, days)
                }) { Text(stringResource(R.string.map_restrict_submit)) }
            },
            dismissButton = {
                TextButton(onClick = { showRestrictDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(segment.name ?: "#${segment.id}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                CompactOutlinedButton(viewModel::clearSelection) { Text(stringResource(R.string.common_close), style = MaterialTheme.typography.labelSmall) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (personalAvoided) {
                    AssistChip(onClick = {}, enabled = false, label = { Text(stringResource(R.string.map_personal_avoid_chip), style = MaterialTheme.typography.labelSmall) })
                }
                if (locked) {
                    AssistChip(onClick = {}, enabled = false, label = { Text(stringResource(R.string.map_locked_chip), style = MaterialTheme.typography.labelSmall) })
                }
                conditions.forEach { c ->
                    AssistChip(onClick = {}, enabled = false, label = { Text(conditionReasonLabel(c.reason), style = MaterialTheme.typography.labelSmall) })
                }
            }
            CompactOutlinedButton({ showRestrictDialog = true }) {
                Text(stringResource(R.string.map_restrict_button), style = MaterialTheme.typography.labelMedium)
            }
            if (personalAvoided || (canEdit && locked)) {
                CompactOutlinedButton({
                    val scope = if (personalAvoided) "personal" else "global"
                    viewModel.restrictSegment(scope, restrictReason, 7, clear = true)
                }) {
                    Text(stringResource(R.string.map_restrict_clear), style = MaterialTheme.typography.labelMedium)
                }
            }
            if (!canEdit) return@Column
            if (state.renamingSegment) {
                OutlinedTextField(state.renameSegmentName, viewModel::updateRenameSegmentName, label = { Text(stringResource(R.string.map_segment_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                CompactButton(viewModel::saveRenameSegment) { Text(stringResource(R.string.map_rename)) }
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CompactOutlinedButton(viewModel::startRenameSegment) { Text(stringResource(R.string.map_rename)) }
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = state.drawSnapEnabled, onCheckedChange = viewModel::setDrawSnapEnabled)
                    Text(stringResource(R.string.map_draw_snap), style = MaterialTheme.typography.labelSmall)
                }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MapEditShapePanel(state: MapUiState, viewModel: MapViewModel) {
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            state.selectedEditVertexIndex?.let { idx ->
                if (idx > 0 && idx < (state.editSegmentPoints?.lastIndex ?: 0)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CompactOutlinedButton(viewModel::toggleMoveEditVertex) {
                            Text(
                                if (state.moveEditVertexIndex == idx) stringResource(R.string.map_move_active)
                                else stringResource(R.string.map_edit_vertex_move),
                            )
                        }
                        CompactOutlinedButton(viewModel::deleteSelectedEditVertex) {
                            Text(stringResource(R.string.map_edit_vertex_delete))
                        }
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CompactButton(viewModel::finishEditSegmentShape) { Text(stringResource(R.string.map_save)) }
                CompactOutlinedButton(viewModel::cancelEditSegmentShape) { Text(stringResource(R.string.common_close)) }
            }
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
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
private val ToolbarPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)

private val CONDITION_REASONS = listOf("muddy", "flooded", "construction", "dog", "icy", "overgrown")

@Composable
private fun conditionReasonLabel(reason: String): String {
    val res = when (reason) {
        "muddy" -> R.string.map_condition_muddy
        "flooded" -> R.string.map_condition_flooded
        "construction" -> R.string.map_condition_construction
        "dog" -> R.string.map_condition_dog
        "icy" -> R.string.map_condition_icy
        "overgrown" -> R.string.map_condition_overgrown
        else -> R.string.map_condition_report
    }
    return stringResource(res)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RestrictScopePicker(selected: String, canEditGlobal: Boolean, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = when (selected) {
        "global" -> if (canEditGlobal) stringResource(R.string.map_restrict_scope_global)
        else stringResource(R.string.map_restrict_scope_recommend)
        else -> stringResource(R.string.map_restrict_scope_personal)
    }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.map_restrict_scope)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.map_restrict_scope_personal)) },
                onClick = { expanded = false; onSelect("personal") },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        if (canEditGlobal) stringResource(R.string.map_restrict_scope_global)
                        else stringResource(R.string.map_restrict_scope_recommend),
                    )
                },
                onClick = { expanded = false; onSelect("global") },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConditionReasonPicker(selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = conditionReasonLabel(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.map_condition_reason)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CONDITION_REASONS.forEach { reason ->
                DropdownMenuItem(
                    text = { Text(conditionReasonLabel(reason)) },
                    onClick = {
                        expanded = false
                        onSelect(reason)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrashPanel(state: MapUiState, viewModel: MapViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val isEmpty = state.deletedNodes.isEmpty() && state.deletedSegments.isEmpty()

    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.map_trash_title), style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) stringResource(R.string.common_close) else stringResource(R.string.map_trash_show))
                }
            }
            if (expanded) {
                if (isEmpty) {
                    Text(stringResource(R.string.map_trash_empty), style = MaterialTheme.typography.bodySmall)
                } else {
                    if (state.deletedNodes.isNotEmpty()) {
                        Text(stringResource(R.string.map_trash_nodes), style = MaterialTheme.typography.labelMedium)
                        state.deletedNodes.forEach { node ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(node.name ?: "#${node.id}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                CompactOutlinedButton({ viewModel.restoreNode(node.id) }) { Text(stringResource(R.string.map_restore), style = MaterialTheme.typography.labelSmall) }
                                CompactOutlinedButton({ viewModel.purgeNode(node.id) }) { Text(stringResource(R.string.map_purge), style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                    }
                    if (state.deletedSegments.isNotEmpty()) {
                        Text(stringResource(R.string.map_trash_segments), style = MaterialTheme.typography.labelMedium)
                        state.deletedSegments.forEach { segment ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(segment.name ?: "#${segment.id}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                CompactOutlinedButton({ viewModel.restoreSegment(segment.id) }) { Text(stringResource(R.string.map_restore), style = MaterialTheme.typography.labelSmall) }
                                CompactOutlinedButton({ viewModel.purgeSegment(segment.id) }) { Text(stringResource(R.string.map_purge), style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class SegmentSortKey { START, END, LENGTH, DURATION, USAGE, NAME }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SegmentsTablePanel(state: MapUiState, viewModel: MapViewModel) {
    var expanded by remember { mutableStateOf(false) }
    var sortKey by remember { mutableStateOf(SegmentSortKey.START) }
    var sortAsc by remember { mutableStateOf(true) }
    val collator = remember { Collator.getInstance() }
    val nodesById = remember(state.nodes) { state.nodes.associateBy { it.id } }
    fun nodeName(id: Int) = nodesById[id]?.name ?: "#$id"

    val sorted = remember(state.segments, sortKey, sortAsc, state.segmentUsage) {
        val rows = state.segments.map { segment ->
            segment to SegmentRowSort(
                start = nodeName(segment.startNodeId),
                end = nodeName(segment.endNodeId),
                length = segment.lengthM,
                duration = segment.durationMin,
                usage = state.segmentUsage[segment.id] ?: 0,
                name = segment.name ?: "",
            )
        }
        rows.sortedWith { a, b ->
            val cmp = when (sortKey) {
                SegmentSortKey.START -> collator.compare(a.second.start, b.second.start)
                SegmentSortKey.END -> collator.compare(a.second.end, b.second.end)
                SegmentSortKey.LENGTH -> a.second.length.compareTo(b.second.length)
                SegmentSortKey.DURATION -> a.second.duration.compareTo(b.second.duration)
                SegmentSortKey.USAGE -> a.second.usage.compareTo(b.second.usage)
                SegmentSortKey.NAME -> collator.compare(a.second.name, b.second.name)
            }
            if (sortAsc) cmp else -cmp
        }
    }

    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.map_segments_table_title, state.segments.size), style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) stringResource(R.string.common_close) else stringResource(R.string.map_segments_table_show))
                }
            }
            if (expanded) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SegmentSortKey.entries.forEach { key ->
                        FilterChip(
                            selected = sortKey == key,
                            onClick = {
                                if (sortKey == key) sortAsc = !sortAsc else { sortKey = key; sortAsc = true }
                            },
                            label = { Text(segmentSortLabel(key), style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
                sorted.take(50).forEach { (segment, row) ->
                    Text(
                        "${row.start} → ${row.end} · ${"%.2f".format(Locale.US, row.length / 1000.0)} km · ${row.duration} min · ${row.usage}× ${segment.name?.let { "· $it" } ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (sorted.size > 50) {
                    Text(stringResource(R.string.map_segments_table_more, sorted.size - 50), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

private data class SegmentRowSort(
    val start: String,
    val end: String,
    val length: Int,
    val duration: Int,
    val usage: Int,
    val name: String,
)

@Composable
private fun segmentSortLabel(key: SegmentSortKey): String = when (key) {
    SegmentSortKey.START -> stringResource(R.string.map_sort_start)
    SegmentSortKey.END -> stringResource(R.string.map_sort_end)
    SegmentSortKey.LENGTH -> stringResource(R.string.map_sort_length)
    SegmentSortKey.DURATION -> stringResource(R.string.map_sort_duration)
    SegmentSortKey.USAGE -> stringResource(R.string.map_sort_usage)
    SegmentSortKey.NAME -> stringResource(R.string.map_sort_name)
}

@Composable
private fun CompactButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = CompactPadding,
    content: @Composable RowScope.() -> Unit,
) {
    Button(onClick, modifier, enabled = enabled, contentPadding = contentPadding, content = content)
}

@Composable
private fun CompactOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = CompactPadding,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(onClick, modifier, enabled = enabled, contentPadding = contentPadding, content = content)
}
