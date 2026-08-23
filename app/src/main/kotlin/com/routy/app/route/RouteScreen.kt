package com.routy.app.route

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.routy.app.R
import com.routy.app.RoutyApplication
import com.routy.app.logic.api.FavoriteEntry
import com.routy.app.logic.api.GeoPoint
import com.routy.app.logic.api.NodeDto
import com.routy.app.logic.api.PointPreviewBreakdown
import com.routy.app.logic.api.RouteDisplayPayload
import com.routy.app.logic.geo.LatLng
import com.routy.app.logic.route.RouteWalkTrackPoint
import com.routy.app.logic.route.VoiceCue
import com.routy.app.logic.route.VoiceCueTracker
import com.routy.app.logic.route.WaypointProgressTracker
import com.routy.app.logic.route.buildRouteWalkGpx
import com.routy.app.logic.route.goldenCanonicalForFinishedHop
import com.routy.app.logic.route.gpxFileName
import com.routy.app.logic.route.remainingRouteGeometry
import com.routy.app.map.BaseMapStyle
import com.routy.app.map.MapStyleSwitcher
import com.routy.app.map.NamePartsInput
import com.routy.app.map.RoutyMapView
import com.routy.app.recording.BatteryOptimizationPrompt
import com.routy.app.ui.OfflineBanner
import java.text.Collator

@Composable
fun RouteScreen(onStartRecording: () -> Unit, accountLocaleTag: String, modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as RoutyApplication
    val activity = LocalContext.current as? android.app.Activity
    val viewModel: RouteViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                RouteViewModel(app.apiClientProvider, app.routeProgressStore, app.routeWalkTrackStore, app.networkCache, app.bootstrapLoader, app.mapTilePrefetchScheduler)
            }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    val clipboard = LocalClipboard.current

    LaunchedEffect(uiState.pendingShareUrl) {
        val url = uiState.pendingShareUrl ?: return@LaunchedEffect
        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Route link", url)))
        viewModel.clearPendingShareUrl()
    }

    DisposableEffect(uiState.keepScreenOn, uiState.trackEnabled) {
        if (uiState.keepScreenOn && uiState.trackEnabled) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    if (uiState.loadingInitial) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val route = uiState.route
    if (route == null) {
        SuggestingMapLayout(
            uiState = uiState,
            viewModel = viewModel,
            modifier = modifier,
        )
    } else {
        RouteWithMapLayout(
            uiState = uiState,
            route = route,
            viewModel = viewModel,
            accountLocaleTag = accountLocaleTag,
            onStartRecording = onStartRecording,
            modifier = modifier,
        )
    }

    uiState.completionPointsEarned?.let {
        CompletionStatsDialog(
            uiState = uiState,
            viewModel = viewModel,
            accountLocaleTag = accountLocaleTag,
            onDismiss = viewModel::dismissCompletionStats,
        )
    }
}

@Composable
private fun routeMessageText(messageRes: Int, messageArgs: List<Any>): String {
    val args = messageArgs.toTypedArray()
    return if (args.isEmpty()) stringResource(messageRes) else stringResource(messageRes, *args)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoutePresetButtons(uiState: RouteUiState, viewModel: RouteViewModel) {
    val loading = uiState.status == RouteStatus.LOADING
    val hasStart = uiState.startNodeId != null || uiState.homeNodeId != null
    val canGenerate = !loading && hasStart && !uiState.offlineCached
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CompactButton(onClick = { viewModel.suggest("short") }, enabled = canGenerate) {
            Text(stringResource(if (loading) R.string.route_generating else R.string.route_preset_short))
        }
        CompactOutlinedButton(onClick = { viewModel.suggest("normal") }, enabled = canGenerate) {
            Text(stringResource(R.string.route_preset_normal))
        }
        CompactOutlinedButton(onClick = { viewModel.suggest("long") }, enabled = canGenerate) {
            Text(stringResource(R.string.route_preset_long))
        }
        CompactOutlinedButton(onClick = { viewModel.surprise() }, enabled = canGenerate) {
            Text(stringResource(R.string.route_preset_surprise))
        }
    }
}

private enum class DockLevel { COLLAPSED, EXPANDED }

@Composable
private fun RouteMapChrome(
    uiState: RouteUiState,
    mapStyle: BaseMapStyle,
    onMapStyle: (BaseMapStyle) -> Unit,
    waymarkedOverlay: Boolean,
    onWaymarkedOverlay: (Boolean) -> Unit,
    routeGeometry: List<GeoPoint>,
    stations: List<com.routy.app.logic.api.RouteStation>,
    goldenSegmentIds: Set<Int>,
    goldenHitIds: Set<Int> = emptySet(),
    fitKey: Any?,
    fitToRouteOnly: Boolean,
    emphasizeNetwork: Boolean,
    completedWaypointIndex: Int = -1,
    trackedGeometry: List<GeoPoint> = emptyList(),
    followEnabled: Boolean = false,
    compassMode: MapCompassMode = MapCompassMode.NORTH_FREE,
    locationBearing: Float? = null,
    locationSpeed: Float? = null,
    onCompassClick: (() -> Unit)? = null,
    planningMode: Boolean = false,
    nodeBadges: Map<Int, String> = emptyMap(),
    segmentBadges: Map<Int, String> = emptyMap(),
    onMapClick: ((Double, Double) -> Unit)? = null,
    onMapLongClick: ((Double, Double) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        RoutyMapView(
            style = mapStyle,
            waymarkedOverlay = waymarkedOverlay,
            nodes = uiState.nodes,
            segments = uiState.segments,
            routeGeometry = routeGeometry,
            stations = stations,
            myLocation = uiState.myLocation,
            fitKey = fitKey,
            fitToRouteOnly = fitToRouteOnly,
            emphasizeNetworkSegments = emphasizeNetwork,
            completedWaypointIndex = completedWaypointIndex,
            trackedGeometry = trackedGeometry,
            followEnabled = followEnabled,
            compassMode = compassMode,
            locationBearing = locationBearing,
            locationSpeed = locationSpeed,
            goldenSegmentIds = goldenSegmentIds,
            goldenHitIds = goldenHitIds,
            homeNodeId = uiState.homeNodeId,
            startNodeId = if (planningMode) uiState.startNodeId else null,
            endNodeId = if (planningMode) uiState.destinationNodeId else null,
            mustVisitNodeIds = if (planningMode) uiState.mustVisitNodeIds else emptyList(),
            isLoop = uiState.isLoop,
            requiredSegmentIds = if (planningMode) uiState.requiredSegmentIds.toSet() else emptySet(),
            excludedSegmentIds = if (planningMode) uiState.excludedSegmentIds.toSet() else emptySet(),
            selectedNodeId = if (planningMode) uiState.selectedNodeId else null,
            selectedSegmentId = if (planningMode) uiState.selectedSegmentId else null,
            nodeBadges = if (planningMode) nodeBadges else emptyMap(),
            segmentBadges = if (planningMode) segmentBadges else emptyMap(),
            onMapClick = onMapClick,
            onMapLongClick = onMapLongClick,
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (uiState.offlineCached) OfflineBanner()
            MapStyleSwitcher(
                selected = mapStyle,
                onSelect = onMapStyle,
                waymarkedOverlay = waymarkedOverlay,
                onWaymarkedOverlayChange = onWaymarkedOverlay,
            )
            if (onCompassClick != null && followEnabled) {
                CompactOutlinedButton(onClick = onCompassClick) {
                    Text(
                        when (compassMode) {
                            MapCompassMode.NORTH_FREE -> stringResource(R.string.route_compass_north_free)
                            MapCompassMode.HEADING_UP -> stringResource(R.string.route_compass_heading_up)
                            MapCompassMode.NORTH_LOCKED -> stringResource(R.string.route_compass_north)
                        },
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteBottomDock(
    level: DockLevel,
    onToggleCollapse: () -> Unit,
    summary: @Composable RowScope.() -> Unit,
    primary: @Composable () -> Unit,
    expandedContent: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 2.dp,
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = summary,
                )
                IconButton(onClick = onToggleCollapse, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = if (level == DockLevel.COLLAPSED) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = stringResource(
                            if (level == DockLevel.COLLAPSED) R.string.route_panel_expand else R.string.route_panel_collapse,
                        ),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            AnimatedVisibility(visible = level == DockLevel.EXPANDED) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    primary()
                    HorizontalDivider()
                    expandedContent()
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuggestingMapLayout(
    uiState: RouteUiState,
    viewModel: RouteViewModel,
    modifier: Modifier = Modifier,
) {
    var mapStyle by remember { mutableStateOf(BaseMapStyle.STREETS) }
    var waymarkedOverlay by remember { mutableStateOf(false) }
    var dockLevel by remember { mutableStateOf(DockLevel.COLLAPSED) }

    val nodeBadges = remember(uiState.startNodeId, uiState.destinationNodeId, uiState.isLoop, uiState.mustVisitNodeIds) {
        buildPlanningNodeBadges(uiState)
    }
    val segmentBadges = remember(uiState.requiredSegmentIds, uiState.excludedSegmentIds, uiState.segments) {
        buildPlanningSegmentBadges(uiState)
    }
    val summaryText = planningSummaryText(uiState)
    val fitKey = remember(uiState.startNodeId, uiState.mustVisitNodeIds, uiState.requiredSegmentIds, uiState.excludedSegmentIds) {
        "plan-${uiState.startNodeId}-${uiState.mustVisitNodeIds.joinToString(",")}-${uiState.requiredSegmentIds.joinToString(",")}"
    }

    Column(modifier = modifier.fillMaxSize()) {
        RouteMapChrome(
            uiState = uiState,
            mapStyle = mapStyle,
            onMapStyle = { mapStyle = it },
            waymarkedOverlay = waymarkedOverlay,
            onWaymarkedOverlay = { waymarkedOverlay = it },
            routeGeometry = emptyList(),
            stations = emptyList(),
            goldenSegmentIds = uiState.todayGoldenSegmentIds,
            fitKey = fitKey,
            fitToRouteOnly = false,
            emphasizeNetwork = true,
            planningMode = true,
            nodeBadges = nodeBadges,
            segmentBadges = segmentBadges,
            onMapClick = viewModel::onPlanningMapClick,
            modifier = Modifier.weight(1f),
        )

        RouteBottomDock(
            level = dockLevel,
            onToggleCollapse = {
                dockLevel = if (dockLevel == DockLevel.COLLAPSED) DockLevel.EXPANDED else DockLevel.COLLAPSED
            },
            summary = {
                Text(
                    summaryText,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            },
            primary = {
                Text(
                    stringResource(R.string.route_planning_tap_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                uiState.selectedNodeId?.let { nodeId ->
                    PlanningNodePanel(nodeId, uiState, viewModel)
                }
                uiState.selectedSegmentId?.let { segmentId ->
                    PlanningSegmentPanel(segmentId, uiState, viewModel)
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    CompactCheck(
                        uiState.isLoop,
                        viewModel::setIsLoop,
                        stringResource(R.string.route_loop),
                        a11yDescription = stringResource(R.string.route_loop_hint),
                    )
                    CompactCheck(uiState.explorerMode, viewModel::setExplorerMode, stringResource(R.string.route_explorer_mode))
                    CompactCheck(uiState.forceGolden, viewModel::setForceGolden, stringResource(R.string.route_force_golden))
                }
                RoutePresetButtons(uiState, viewModel)
                if (uiState.usingNetworkFallback != null) {
                    Text(
                        stringResource(
                            if (uiState.usingNetworkFallback == true) {
                                R.string.route_length_taste_network
                            } else {
                                R.string.route_length_taste_personal
                            },
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FavoritesLoadDeleteButton(uiState.favorites, uiState.status == RouteStatus.LOADING, viewModel)
                uiState.messageRes?.let {
                    Text(
                        routeMessageText(it, uiState.messageArgs),
                        color = if (uiState.status == RouteStatus.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            },
            expandedContent = {},
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanningNodePanel(nodeId: Int, uiState: RouteUiState, viewModel: RouteViewModel) {
    val node = uiState.nodes.firstOrNull { it.id == nodeId } ?: return
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f), shape = MaterialTheme.shapes.small) {
        Column(Modifier.fillMaxWidth().padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(node.name ?: "#${node.id}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CompactOutlinedButton(onClick = { viewModel.setNodeAsStart(nodeId) }) {
                    Text(stringResource(R.string.route_node_menu_start))
                }
                if (!uiState.isLoop) {
                    CompactOutlinedButton(onClick = { viewModel.setNodeAsEnd(nodeId) }) {
                        Text(stringResource(R.string.route_node_menu_end))
                    }
                }
                CompactOutlinedButton(onClick = { viewModel.toggleMustVisit(nodeId) }) {
                    Text(stringResource(R.string.route_node_menu_must_visit))
                }
                CompactOutlinedButton(onClick = viewModel::clearSelectedNodeRole) {
                    Text(stringResource(R.string.route_node_menu_clear))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanningSegmentPanel(segmentId: Int, uiState: RouteUiState, viewModel: RouteViewModel) {
    val segment = uiState.segments.firstOrNull { it.id == segmentId }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f), shape = MaterialTheme.shapes.small) {
        Column(Modifier.fillMaxWidth().padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(segment?.name ?: "#$segmentId", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CompactOutlinedButton(onClick = { viewModel.setSegmentRequired(segmentId) }) {
                    Text(stringResource(R.string.route_segment_menu_required))
                }
                CompactOutlinedButton(onClick = { viewModel.setSegmentExcluded(segmentId) }) {
                    Text(stringResource(R.string.route_segment_menu_excluded))
                }
                CompactOutlinedButton(onClick = { viewModel.clearSegmentConstraint(segmentId) }) {
                    Text(stringResource(R.string.route_segment_menu_clear))
                }
            }
        }
    }
}

@Composable
private fun planningSummaryText(uiState: RouteUiState): String {
    val startLabel = nodeLabel(uiState, uiState.startNodeId ?: uiState.homeNodeId)
    val parts = buildList {
        if (uiState.isLoop) {
            add(stringResource(R.string.route_summary_loop, startLabel))
        } else {
            add(
                stringResource(
                    R.string.route_summary_point_to_point,
                    startLabel,
                    nodeLabel(uiState, uiState.destinationNodeId ?: uiState.homeNodeId),
                ),
            )
        }
        if (uiState.mustVisitNodeIds.isNotEmpty()) {
            add(stringResource(R.string.route_summary_must_visit, uiState.mustVisitNodeIds.size))
        }
        if (uiState.requiredSegmentIds.isNotEmpty()) {
            val names = uiState.requiredSegmentIds.joinToString(", ") { id ->
                uiState.segments.firstOrNull { it.id == id }?.name ?: "#$id"
            }
            add(stringResource(R.string.route_summary_required_names, names))
        }
        if (uiState.excludedSegmentIds.isNotEmpty()) {
            val names = uiState.excludedSegmentIds.joinToString(", ") { id ->
                uiState.segments.firstOrNull { it.id == id }?.name ?: "#$id"
            }
            add(stringResource(R.string.route_summary_excluded_names, names))
        }
    }
    return parts.joinToString(" · ")
}

@Composable
private fun nodeLabel(uiState: RouteUiState, nodeId: Int?): String {
    if (nodeId == null) return "…"
    val name = uiState.nodes.firstOrNull { it.id == nodeId }?.name ?: "#$nodeId"
    return if (nodeId == uiState.homeNodeId) "$name (${stringResource(R.string.map_node_home)})" else name
}

private fun buildPlanningNodeBadges(uiState: RouteUiState): Map<Int, String> {
    val badges = mutableMapOf<Int, String>()
    uiState.startNodeId?.let { badges[it] = "S" }
    if (!uiState.isLoop) {
        uiState.destinationNodeId?.let { badges[it] = "E" }
    }
    uiState.mustVisitNodeIds.forEachIndexed { index, id ->
        badges[id] = if (uiState.mustVisitNodeIds.size > 1) "${index + 1}" else "★"
    }
    return badges
}

private fun buildPlanningSegmentBadges(uiState: RouteUiState): Map<Int, String> {
    val badges = mutableMapOf<Int, String>()
    uiState.requiredSegmentIds.forEach { id ->
        val name = uiState.segments.firstOrNull { it.id == id }?.name ?: "#$id"
        badges[id] = "R · $name"
    }
    uiState.excludedSegmentIds.forEach { id ->
        val name = uiState.segments.firstOrNull { it.id == id }?.name ?: "#$id"
        badges[id] = "E · $name"
    }
    return badges
}

@Composable
private fun RouteWithMapLayout(
    uiState: RouteUiState,
    route: RouteDisplayPayload,
    viewModel: RouteViewModel,
    accountLocaleTag: String,
    onStartRecording: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var mapStyle by remember { mutableStateOf(BaseMapStyle.STREETS) }
    var waymarkedOverlay by remember { mutableStateOf(false) }
    var dockLevel by remember { mutableStateOf(DockLevel.EXPANDED) }
    var hasLocationPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasLocationPermission = granted
        if (granted) viewModel.setFollowEnabled(true)
    }
    fun needsNotificationPermission() =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    var pendingStartBackground by remember { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (pendingStartBackground) {
            pendingStartBackground = false
            if (uiState.trackEnabled) viewModel.setTrackEnabled(true)
            else if (uiState.voiceEnabled) { /* voice already set */ }
        }
    }
    val backgroundLocationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasLocationPermission = granted
        if (!granted) {
            pendingStartBackground = false
            return@rememberLauncherForActivityResult
        }
        if (needsNotificationPermission()) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            pendingStartBackground = false
        }
    }
    fun ensureBackgroundPermissions(then: () -> Unit) {
        when {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ->
                backgroundLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            needsNotificationPermission() -> {
                pendingStartBackground = true
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            else -> then()
        }
    }
    var pendingDiscard by remember { mutableStateOf(false) }

    if (pendingDiscard) {
        AlertDialog(
            onDismissRequest = { pendingDiscard = false },
            confirmButton = {
                TextButton(onClick = {
                    pendingDiscard = false
                    RouteTrackingForegroundService.stopAll(context)
                    viewModel.discardActive()
                }) {
                    Text(stringResource(R.string.route_discard_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDiscard = false }) {
                    Text(stringResource(R.string.route_cancel))
                }
            },
            text = { Text(stringResource(R.string.route_discard_confirm)) },
        )
    }

    val needsService = RouteTrackingForegroundService.needsForegroundService(uiState.trackEnabled, uiState.voiceEnabled)
    ActiveRouteLocationEffect(uiState, hasLocationPermission && !needsService, viewModel)
    if (uiState.mode == RouteMode.ACTIVE) {
        ActiveRouteTrackingServiceEffect(uiState, route, viewModel, accountLocaleTag)
        if (!needsService) {
            ActiveTrackingEffects(uiState, route, viewModel, accountLocaleTag)
        }
    }

    val loading = uiState.status == RouteStatus.LOADING
    val stationPath = remember(route.shortStationGroups) {
        route.shortStationGroups.joinToString(" › ") { group ->
            val via = group.viaSegmentName
            if (via.isNullOrBlank()) group.text else "${group.text} (via $via)"
        }
    }

    val displayRouteGeometry = remember(route.geometry, route.stations, uiState.completedWaypointIndex) {
        if (uiState.completedWaypointIndex >= 0) {
            remainingRouteGeometry(route.geometry, route.stations, uiState.completedWaypointIndex)
        } else {
            route.geometry
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        RouteMapChrome(
            uiState = uiState,
            mapStyle = mapStyle,
            onMapStyle = { mapStyle = it },
            waymarkedOverlay = waymarkedOverlay,
            onWaymarkedOverlay = { waymarkedOverlay = it },
            routeGeometry = displayRouteGeometry,
            stations = route.stations,
            goldenSegmentIds = uiState.todayGoldenSegmentIds,
            goldenHitIds = uiState.goldenHitIds,
            fitKey = uiState.token.ifEmpty { route.nodeChain.joinToString("-") },
            fitToRouteOnly = true,
            emphasizeNetwork = false,
            completedWaypointIndex = uiState.completedWaypointIndex,
            trackedGeometry = uiState.trackedGeometry,
            followEnabled = uiState.followEnabled,
            compassMode = uiState.compassMode,
            locationBearing = uiState.locationBearing,
            locationSpeed = uiState.locationSpeed,
            onCompassClick = if (uiState.followEnabled) viewModel::cycleCompassMode else null,
            onMapLongClick = if (uiState.mode == RouteMode.ACTIVE) viewModel::onActiveMapLongPress else null,
            modifier = Modifier.weight(1f),
        )

        uiState.retagNodeId?.let { _ ->
            val lat = uiState.retagLat ?: 0.0
            val lng = uiState.retagLng ?: 0.0
            AlertDialog(
                onDismissRequest = viewModel::dismissRetag,
                title = { Text(stringResource(R.string.route_retag_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        NamePartsInput(
                            lat = lat,
                            lng = lng,
                            part1 = uiState.retagPart1,
                            part2 = uiState.retagPart2,
                            onPart1 = viewModel::updateRetagPart1,
                            onPart2 = viewModel::updateRetagPart2,
                            prefillPart1 = false,
                        )
                        if (uiState.retagOfferMove) {
                            Text(
                                stringResource(R.string.route_retag_move_offer),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = viewModel::saveRetagNode, enabled = !uiState.retagSaving) {
                        Text(stringResource(R.string.map_rename))
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissRetag) {
                        Text(stringResource(R.string.common_close))
                    }
                },
            )
        }

        RouteBottomDock(
            level = dockLevel,
            onToggleCollapse = {
                dockLevel = if (dockLevel == DockLevel.COLLAPSED) DockLevel.EXPANDED else DockLevel.COLLAPSED
            },
            summary = {
                CompactMeta("${"%.1f".format(route.lengthM / 1000.0)} km")
                CompactMeta("${route.durationMin} min")
                route.elevation?.let { CompactMeta("↗${it.gainM}m") }
                if (uiState.mode == RouteMode.ACTIVE && uiState.trackEnabled) {
                    val done = if (uiState.completedWaypointIndex < 0) 0 else uiState.completedWaypointIndex + 1
                    CompactMeta("$done/${route.stations.size}")
                }
            },
            primary = {
                if (stationPath.isNotBlank()) {
                    Text(
                        stationPath,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                uiState.pointPreview?.let { preview ->
                    Text(
                        stringResource(R.string.route_point_preview_total, preview.total),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                    )
                }
                if (uiState.goldenHitIds.isNotEmpty()) {
                    Text(
                        stringResource(R.string.route_golden_hint, uiState.goldenHitIds.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                uiState.pendingShareToken?.let { token ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            uiState.sharedRouteName ?: stringResource(R.string.route_share_preview),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        CompactButton(onClick = { viewModel.acceptSharedRoute(token) }) {
                            Text(stringResource(R.string.route_accept))
                        }
                        CompactOutlinedButton(onClick = viewModel::dismissSharedRoute) {
                            Text(stringResource(R.string.route_share_dismiss))
                        }
                    }
                }

                when {
                    uiState.mode == RouteMode.SUGGESTING && uiState.pendingShareToken == null -> {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            CompactOutlinedButton(onClick = { viewModel.adjust("shorter") }, enabled = !loading) {
                                Text(stringResource(R.string.route_shorter))
                            }
                            CompactOutlinedButton(onClick = { viewModel.adjust("longer") }, enabled = !loading) {
                                Text(stringResource(R.string.route_longer))
                            }
                            CompactOutlinedButton(onClick = viewModel::another, enabled = !loading) {
                                Text(stringResource(R.string.route_new_route))
                            }
                            CompactButton(onClick = viewModel::accept, enabled = !loading) {
                                Text(stringResource(R.string.route_accept))
                            }
                            CompactOutlinedButton(onClick = viewModel::cancel, enabled = !loading) {
                                Text(stringResource(R.string.route_cancel))
                            }
                        }
                    }
                    uiState.mode == RouteMode.ACTIVE -> {
                        Text(
                            stringResource(R.string.route_walk_controls_label),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
                            CompactCheck(
                                checked = uiState.followEnabled,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        if (hasLocationPermission) viewModel.setFollowEnabled(true)
                                        else permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                    } else {
                                        RouteTrackingForegroundService.stopAll(context)
                                        viewModel.setFollowEnabled(false)
                                    }
                                },
                                label = stringResource(R.string.route_follow_check),
                            )
                            CompactCheck(
                                checked = uiState.trackEnabled,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        ensureBackgroundPermissions {
                                            viewModel.setTrackEnabled(true)
                                        }
                                    } else {
                                        RouteTrackingForegroundService.stopTrack(context)
                                        viewModel.setTrackEnabled(false)
                                    }
                                },
                                label = stringResource(R.string.route_track_check),
                            )
                            CompactCheck(
                                checked = uiState.voiceEnabled,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        ensureBackgroundPermissions {
                                            viewModel.setVoiceEnabled(true)
                                        }
                                    } else {
                                        viewModel.setVoiceEnabled(false)
                                    }
                                },
                                label = stringResource(R.string.route_voice_check),
                            )
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (uiState.trackedGeometry.size >= 2) {
                                CompactOutlinedButton(onClick = {
                                    shareRouteWalkGpx(context, viewModel.trackPointsForComplete(route), route)
                                }) {
                                    Text(stringResource(R.string.route_export_gpx))
                                }
                            }
                            val canComplete = !uiState.trackEnabled || uiState.completedWaypointIndex >= route.stations.lastIndex
                            CompactButton(onClick = viewModel::complete, enabled = canComplete) {
                                Text(stringResource(R.string.route_complete_button))
                            }
                            CompactOutlinedButton(onClick = { pendingDiscard = true }) {
                                Text(stringResource(R.string.route_discard_button))
                            }
                        }
                        if (uiState.trackEnabled || uiState.voiceEnabled) {
                            BatteryOptimizationPrompt(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }

                uiState.messageRes?.let {
                    Text(
                        routeMessageText(it, uiState.messageArgs),
                        color = if (uiState.status == RouteStatus.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            },
            expandedContent = {
                when {
                    uiState.mode == RouteMode.SUGGESTING && uiState.pendingShareToken == null -> {
                        RoutePresetButtons(uiState, viewModel)
                        CompactOutlinedButton(onClick = onStartRecording, enabled = !loading) {
                            Text(stringResource(R.string.record_entry_point))
                        }
                        uiState.pointPreview?.let { PointPreviewLines(it) }
                    }
                    uiState.mode == RouteMode.ACTIVE -> {
                        DenseTextField(
                            value = uiState.nickname,
                            onValueChange = viewModel::setNickname,
                            label = stringResource(R.string.route_name_label),
                        )
                        CompactOutlinedButton(onClick = viewModel::saveNickname, enabled = !uiState.nicknameSaving) {
                            Text(stringResource(R.string.route_save_name))
                        }
                        CompactCheck(uiState.keepScreenOn, viewModel::setKeepScreenOn, stringResource(R.string.route_keep_screen_on))
                    }
                }
            },
        )
    }
}

@Composable
private fun CompactMeta(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}

@Composable
private fun CompactCheck(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    enabled: Boolean = true,
    a11yDescription: String? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.heightIn(max = 28.dp),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.scale(0.8f),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            modifier = if (a11yDescription != null) {
                Modifier.semantics { contentDescription = a11yDescription }
            } else {
                Modifier
            },
        )
    }
}

@Composable
private fun DenseTextField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        singleLine = true,
        textStyle = MaterialTheme.typography.labelSmall,
        modifier = Modifier.fillMaxWidth().heightIn(max = 52.dp),
        colors = OutlinedTextFieldDefaults.colors(),
    )
}

@Composable
private fun ActiveRouteLocationEffect(uiState: RouteUiState, hasLocationPermission: Boolean, viewModel: RouteViewModel) {
    val context = LocalContext.current
    DisposableEffect(uiState.followEnabled, hasLocationPermission, uiState.trackEnabled, uiState.voiceEnabled) {
        val needsService = RouteTrackingForegroundService.needsForegroundService(uiState.trackEnabled, uiState.voiceEnabled)
        if (!uiState.followEnabled || !hasLocationPermission || needsService) {
            return@DisposableEffect onDispose {}
        }
        val client = LocationServices.getFusedLocationProviderClient(context)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L).build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    viewModel.setMyLocation(GeoPoint(loc.latitude, loc.longitude))
                    viewModel.addRetagLocationSample(
                        loc.latitude,
                        loc.longitude,
                        if (loc.hasAccuracy()) loc.accuracy else null,
                    )
                    viewModel.setLocationMotion(
                        if (loc.hasBearing()) loc.bearing else null,
                        if (loc.hasSpeed()) loc.speed else null,
                    )
                }
            }
        }
        requestLocationUpdatesIfPermitted(context, client, request, callback)
        onDispose { client.removeLocationUpdates(callback) }
    }
}

@Composable
private fun ActiveRouteTrackingServiceEffect(
    uiState: RouteUiState,
    route: RouteDisplayPayload,
    viewModel: RouteViewModel,
    accountLocaleTag: String,
) {
    val context = LocalContext.current
    var service by remember { mutableStateOf<RouteTrackingForegroundService?>(null) }
    val routeKey = remember(route.nodeChain) { route.nodeChain.joinToString("-") }

    val needsService = RouteTrackingForegroundService.needsForegroundService(uiState.trackEnabled, uiState.voiceEnabled)

    LaunchedEffect(needsService) {
        if (!needsService) {
            RouteTrackingForegroundService.stopAll(context)
            service = null
        }
    }

    DisposableEffect(needsService, routeKey, accountLocaleTag) {
        if (!needsService) {
            return@DisposableEffect onDispose {}
        }

        RouteTrackingForegroundService.start(
            context = context,
            stations = route.stations,
            routeGeometry = route.geometry,
            segments = uiState.segments,
            routeSegmentIds = route.segmentIds,
            todayGoldenCanonicalIds = uiState.todayGoldenSegmentIds,
            goldenHitSegmentIds = uiState.goldenHitIds,
            routeKey = routeKey,
            accountLocaleTag = accountLocaleTag,
            trackEnabled = uiState.trackEnabled,
            voiceEnabled = uiState.voiceEnabled,
            completedWaypointIndex = uiState.completedWaypointIndex,
            voiceAnnouncedIndex = uiState.voiceAnnouncedIndex,
        )

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                service = (binder as RouteTrackingForegroundService.LocalBinder).getService()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
            }
        }
        context.bindService(
            Intent(context, RouteTrackingForegroundService::class.java),
            connection,
            Context.BIND_AUTO_CREATE,
        )
        onDispose {
            runCatching { context.unbindService(connection) }
        }
    }

    LaunchedEffect(needsService, uiState.trackEnabled, uiState.voiceEnabled) {
        if (needsService) {
            RouteTrackingForegroundService.setTrackEnabled(context, uiState.trackEnabled)
            RouteTrackingForegroundService.setVoiceEnabled(context, uiState.voiceEnabled)
        }
    }

    LaunchedEffect(service) {
        val bound = service ?: return@LaunchedEffect
        bound.state.collect { serviceState ->
            serviceState.myLocation?.let(viewModel::setMyLocation)
            viewModel.setLocationMotion(serviceState.locationBearing, serviceState.locationSpeed)
            viewModel.setTrackedGeometry(serviceState.trackedGeometry)
            viewModel.syncFromTrackingService(
                completedWaypointIndex = serviceState.completedWaypointIndex,
                voiceAnnouncedIndex = serviceState.voiceAnnouncedIndex,
                trackingActive = serviceState.active,
                trackEnabled = serviceState.trackEnabled,
                myLocation = serviceState.myLocation,
                locationBearing = serviceState.locationBearing,
                locationSpeed = serviceState.locationSpeed,
                trackedGeometry = serviceState.trackedGeometry,
            )
            if (serviceState.autoCompleteRequested) {
                bound.consumeAutoCompleteRequest()
                viewModel.setTrackEnabled(false)
                viewModel.complete()
            }
        }
    }

    LaunchedEffect(service) {
        val bound = service ?: return@LaunchedEffect
        bound.stopTrackRequested.collect {
            viewModel.setTrackEnabled(false)
        }
    }
}

@Composable
private fun ActiveTrackingEffects(
    uiState: RouteUiState,
    route: RouteDisplayPayload,
    viewModel: RouteViewModel,
    accountLocaleTag: String,
) {
    val context = LocalContext.current
    val voiceController = rememberVoiceGuidanceController(accountLocaleTag)
    val cueController = remember(context) { TrackCueController(context) }
    DisposableEffect(Unit) { onDispose { cueController.release() } }

    val voiceTracker = remember(route.nodeChain) {
        VoiceCueTracker(route.stations).also { it.restore(uiState.voiceAnnouncedIndex) }
    }
    val progressTracker = remember(route.nodeChain) {
        WaypointProgressTracker(route.stations).also { it.restore(uiState.completedWaypointIndex) }
    }
    var pendingCue by remember(route.nodeChain) { mutableStateOf<VoiceCue?>(null) }
    val goldenSoundCued = remember(route.nodeChain) { mutableSetOf<Int>() }

    LaunchedEffect(uiState.completedWaypointIndex, route.nodeChain) {
        progressTracker.restore(uiState.completedWaypointIndex)
    }
    LaunchedEffect(uiState.voiceAnnouncedIndex, route.nodeChain) {
        voiceTracker.restore(uiState.voiceAnnouncedIndex)
    }

    val location = uiState.myLocation
    val voiceActive = uiState.voiceEnabled && uiState.followEnabled && !RouteTrackingForegroundService.needsForegroundService(uiState.trackEnabled, uiState.voiceEnabled)
    LaunchedEffect(location, voiceActive, uiState.trackEnabled) {
        if (location == null) return@LaunchedEffect
        val latLng = LatLng(location.lat, location.lng)
        if (voiceActive) voiceTracker.onLocationUpdate(latLng)?.let { pendingCue = it }
        if (uiState.trackEnabled) {
            progressTracker.onLocationUpdate(latLng)?.let { completed ->
                if (completed > uiState.completedWaypointIndex) {
                    for (arrived in (uiState.completedWaypointIndex + 1)..completed) {
                        goldenCanonicalForFinishedHop(
                            arrivedStationIndex = arrived,
                            routeSegmentIds = route.segmentIds,
                            todayGoldenCanonicalIds = uiState.todayGoldenSegmentIds,
                            segments = uiState.segments,
                            alreadyCued = goldenSoundCued,
                        )?.let { canon ->
                            goldenSoundCued.add(canon)
                            cueController.goldenHit()
                        }
                    }
                    viewModel.onWaypointCompleted(completed)
                    if (completed >= route.stations.lastIndex) {
                        cueController.routeCompleted()
                    } else {
                        cueController.waypointReached()
                    }
                }
            }
        }
    }

    pendingCue?.let { cue ->
        val spokenText = cue.toSpokenText(context, accountLocaleTag)
        LaunchedEffect(cue) {
            voiceController.speak(spokenText)
            viewModel.onVoiceCueAnnounced(voiceTracker.announcedCount())
            pendingCue = null
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun FavoritesLoadDeleteButton(
    favorites: List<FavoriteEntry>,
    loading: Boolean,
    viewModel: RouteViewModel,
) {
    if (favorites.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<FavoriteEntry?>(null) }

    CompactOutlinedButton(onClick = { open = true }) {
        Text(stringResource(R.string.route_favorites_title) + " (${favorites.size})")
    }

    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.route_favorites_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    favorites.forEach { fav ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "${fav.name} · ${"%.1f".format(fav.display.lengthM / 1000.0)} km",
                                style = MaterialTheme.typography.labelMedium,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                CompactButton(
                                    onClick = {
                                        viewModel.takeFavorite(fav)
                                        open = false
                                    },
                                    enabled = !loading,
                                ) {
                                    Text(stringResource(R.string.route_favorite_take))
                                }
                                CompactOutlinedButton(onClick = { pendingDelete = fav }) {
                                    Text(stringResource(R.string.route_favorite_delete))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { open = false }) {
                    Text(stringResource(R.string.route_cancel))
                }
            },
        )
    }

    pendingDelete?.let { fav ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteFavorite(fav.id)
                    pendingDelete = null
                }) { Text(stringResource(R.string.route_favorite_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.route_cancel)) }
            },
            text = { Text(stringResource(R.string.route_favorite_delete_confirm)) },
        )
    }
}

@SuppressLint("MissingPermission")
private fun requestLocationUpdatesIfPermitted(
    context: android.content.Context,
    client: com.google.android.gms.location.FusedLocationProviderClient,
    request: LocationRequest,
    callback: LocationCallback,
) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
    client.requestLocationUpdates(request, callback, Looper.getMainLooper())
}

private val CompactButtonPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)

@Composable
private fun CompactButton(onClick: () -> Unit, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        contentPadding = CompactButtonPadding,
        modifier = Modifier.heightIn(min = 28.dp, max = 30.dp),
        colors = ButtonDefaults.buttonColors(),
    ) {
        CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.labelSmall) {
            content()
        }
    }
}

@Composable
private fun CompactOutlinedButton(onClick: () -> Unit, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = CompactButtonPadding,
        modifier = Modifier.heightIn(min = 28.dp, max = 30.dp),
    ) {
        CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.labelSmall) {
            content()
        }
    }
}

@Composable
private fun CompletionStatsDialog(
    uiState: RouteUiState,
    viewModel: RouteViewModel,
    accountLocaleTag: String,
    onDismiss: () -> Unit,
) {
    val pointsEarned = checkNotNull(uiState.completionPointsEarned)
    val tier = uiState.completionCelebrationTier
    val tierTitle = celebrationTitle(tier)
    val context = LocalContext.current
    val voiceController = rememberVoiceGuidanceController(accountLocaleTag)
    val cueController = remember(context) { TrackCueController(context) }
    DisposableEffect(Unit) { onDispose { cueController.release() } }

    val celebrationSpoken = stringResource(R.string.route_celebration)
    LaunchedEffect(pointsEarned, tier) {
        if (tier != "normal") {
            cueController.celebration()
            voiceController.speak(celebrationSpoken)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_ok)) } },
        title = { Text(tierTitle ?: stringResource(R.string.route_completion_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(
                        R.string.route_completion_points,
                        pointsEarned,
                        uiState.completionStreakMultiplier ?: 1.0,
                    ),
                )
                uiState.completionPointBreakdown?.let { breakdown ->
                    PointPreviewLines(breakdown)
                }
                if (uiState.completionGoldenHits > 0) {
                    Text(stringResource(R.string.route_completion_golden_hits, uiState.completionGoldenHits))
                }
                uiState.completionCurrentStreak?.let {
                    Text(stringResource(R.string.route_completion_streak, it))
                }
                uiState.completionWeeklyPoints?.let {
                    Text(stringResource(R.string.route_completion_weekly, it))
                }
                if (uiState.completionNewAchievements.isNotEmpty()) {
                    Text(stringResource(R.string.route_completion_achievements_title), fontWeight = FontWeight.SemiBold)
                    uiState.completionNewAchievements.forEach { label ->
                        Text(stringResource(R.string.route_completion_new_achievement, label))
                    }
                }

                Text(stringResource(R.string.route_length_rating_title), fontWeight = FontWeight.Medium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    lengthRatingOptions().forEach { (rating, labelRes) ->
                        val selected = uiState.completionLengthRating == rating
                        if (selected) {
                            CompactButton(
                                onClick = {},
                                enabled = !uiState.completionRatingSaving,
                            ) { Text(stringResource(labelRes)) }
                        } else {
                            CompactOutlinedButton(
                                onClick = { viewModel.submitLengthRating(rating) },
                                enabled = !uiState.completionRatingSaving,
                            ) { Text(stringResource(labelRes)) }
                        }
                    }
                }
                if (uiState.completionRatingSaved) {
                    Text(stringResource(R.string.route_rating_saved), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }

                if (uiState.completionRouteSnapshot != null) {
                    Text(stringResource(R.string.route_save_as_favorite_prompt), style = MaterialTheme.typography.labelSmall)
                    DenseTextField(
                        value = uiState.completionFavoriteName,
                        onValueChange = viewModel::setCompletionFavoriteName,
                        label = stringResource(R.string.route_favorite_name_placeholder),
                    )
                    CompactOutlinedButton(
                        onClick = viewModel::saveFavoriteAfterComplete,
                        enabled = !uiState.savingFavorite && uiState.completionFavoriteName.isNotBlank(),
                    ) {
                        Text(stringResource(R.string.route_save_favorite))
                    }
                }
            }
        },
    )
}

private fun lengthRatingOptions(): List<Pair<Int, Int>> = listOf(
    1 to R.string.route_length_rating_very_short,
    2 to R.string.route_length_rating_short,
    3 to R.string.route_length_rating_normal,
    4 to R.string.route_length_rating_long,
    5 to R.string.route_length_rating_very_long,
)

@Composable
private fun PointPreviewLines(preview: PointPreviewBreakdown) {
    Text(stringResource(R.string.route_point_preview_base, preview.base), style = MaterialTheme.typography.labelSmall)
    if (preview.golden > 0) {
        Text(stringResource(R.string.route_point_preview_golden, preview.golden), style = MaterialTheme.typography.labelSmall)
    }
    if (preview.exploration > 0) {
        Text(stringResource(R.string.route_point_preview_exploration, preview.exploration), style = MaterialTheme.typography.labelSmall)
    }
    if (preview.diversity > 0) {
        Text(stringResource(R.string.route_point_preview_diversity, preview.diversity), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun celebrationTitle(tier: String): String? = when (tier) {
    "golden" -> stringResource(R.string.route_celebration_golden)
    "streak" -> stringResource(R.string.route_celebration_streak)
    "achievement" -> stringResource(R.string.route_celebration_achievement)
    else -> null
}

private fun shareRouteWalkGpx(
    context: Context,
    points: List<RouteWalkTrackPoint>,
    route: RouteDisplayPayload,
) {
    if (points.isEmpty()) return
    val gpx = buildRouteWalkGpx(points, route.nodeChain.joinToString("-"))
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/gpx+xml"
        putExtra(Intent.EXTRA_TEXT, gpx)
        putExtra(Intent.EXTRA_SUBJECT, gpxFileName())
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.route_export_gpx)))
}
