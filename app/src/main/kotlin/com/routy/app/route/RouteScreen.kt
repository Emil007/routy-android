package com.routy.app.route

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Explore
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
import com.routy.app.R
import com.routy.app.RoutyApplication
import com.routy.app.logic.api.FavoriteEntry
import com.routy.app.logic.api.GeoPoint
import com.routy.app.logic.api.NodeDto
import com.routy.app.logic.api.PointPreviewBreakdown
import com.routy.app.logic.api.RouteDisplayPayload
import com.routy.app.logic.api.RouteStation
import com.routy.app.logic.geo.LatLng
import com.routy.app.logic.route.remainingRouteGeometry
import com.routy.app.map.BaseMapStyle
import com.routy.app.map.rememberMapPreferences
import com.routy.app.map.NamePartsInput
import com.routy.app.map.RoutyMapView
import com.routy.app.recording.BatteryOptimizationPrompt
import com.routy.app.ui.OfflineBanner
import java.text.Collator

@Composable
fun RouteScreen(onStartRecording: () -> Unit, accountLocaleTag: String, modifier: Modifier = Modifier, chromeHosted: Boolean = false, section: RouteSection = RouteSection.FULL) {
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
    val isLive = uiState.mode == RouteMode.ACTIVE || uiState.freeRoamActive
    // Gehen-Sheet (Frei): zeigt IMMER den Free-Roam-Start (kein Rundweg-Formular
    // mit Kurz/Normal/Lang + Go — das gehört ausschließlich zu Route/Planen).
    // free-roam = „geh vom aktuellen Node aus, wohin du willst“; eine generierte
    // Runde macht hier keinen Sinn (Punkt 4 / Emil).
    when {
        // Go-Sheet: nichts aktiv → FREE ROAM starten, nie Rundweg-Generierung.
        chromeHosted && section == RouteSection.GO && route == null && !isLive -> {
            SuggestingMapLayout(
                uiState = uiState,
                viewModel = viewModel,
                modifier = modifier,
                chromeHosted = chromeHosted,
                section = section,
            )
        }
        // Plan-Sheet: sobald eine Live-Session läuft, gehört sie zu Gehen (J3) —
        // nichts zeigen. AUSNAHME: Route ist übernommen (ACTIVE) aber noch nicht
        // gestartet (trackEnabled=false) → Bestätigungsseite mit Go bleibt im
        // Plan-Sheet (Dock zeigt Route, kein Frei-Sprung). Erst Go (accept)
        // schaltet trackEnabled an und kippt die Hülle auf Gehen.
        chromeHosted && section == RouteSection.PLAN && isLive && uiState.trackEnabled -> Unit
        // Go-Sheet: nichts aktiv, aber Vorschau-Route → der Zustand ist im Plan-Sheet (J2-Übergang).
        chromeHosted && section == RouteSection.GO && !isLive && route != null -> Unit
        route == null -> {
            SuggestingMapLayout(
                uiState = uiState,
                viewModel = viewModel,
                modifier = modifier,
                chromeHosted = chromeHosted,
                section = section,
            )
        }
        else -> {
            RouteWithMapLayout(
                uiState = uiState,
                route = route,
                viewModel = viewModel,
                accountLocaleTag = accountLocaleTag,
                onStartRecording = onStartRecording,
                modifier = modifier,
                chromeHosted = chromeHosted,
                section = section,
            )
        }
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
private fun LengthPresetSelector(selected: String, onSelect: (String) -> Unit, enabled: Boolean) {
    val presets = listOf(
        "short" to R.string.route_preset_short_btn,
        "normal" to R.string.route_preset_normal_btn,
        "long" to R.string.route_preset_long_btn,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        presets.forEach { (preset, labelRes) ->
            if (preset == selected) {
                CompactButton(onClick = { onSelect(preset) }, enabled = enabled) {
                    Text(stringResource(labelRes))
                }
            } else {
                CompactOutlinedButton(onClick = { onSelect(preset) }, enabled = enabled) {
                    Text(stringResource(labelRes))
                }
            }
        }
    }
}
private enum class DockLevel { COLLAPSED, EXPANDED }

/** In welcher Sheet-Stufe die Route-Fläche läuft (J3): Planen, Gehen, oder komplex (alt). */
enum class RouteSection { FULL, PLAN, GO }

@Composable
private fun RouteMapChrome(
    uiState: RouteUiState,
    mapStyle: BaseMapStyle,
    waymarkedOverlay: Boolean,
    routeGeometry: List<GeoPoint>,
    stations: List<com.routy.app.logic.api.RouteStation>,
    goldenSegmentIds: Set<Int>,
    goldenHitIds: Set<Int> = emptySet(),
    disconnectedSegmentIds: Set<Int> = emptySet(),
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
    chromeHosted: Boolean = false,
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (!chromeHosted) {
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
            disconnectedSegmentIds = disconnectedSegmentIds,
            homeNodeId = uiState.homeNodeId,
            startNodeId = when {
                planningMode -> uiState.startNodeId
                // Free-roam (D2 parity): the start node is your position's
                // nearest node — render it brown (#a5711c) like the web.
                uiState.freeRoamActive -> uiState.freeRoamStartNodeId
                else -> null
            },
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
        }
        Column(
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (uiState.offlineCached) OfflineBanner()
            if (onCompassClick != null) {
                IconButton(onClick = onCompassClick, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.Explore,
                        contentDescription = when (compassMode) {
                            MapCompassMode.NORTH_FREE -> stringResource(R.string.route_compass_north_free)
                            MapCompassMode.HEADING_UP -> stringResource(R.string.route_compass_heading_up)
                            MapCompassMode.NORTH_LOCKED -> stringResource(R.string.route_compass_north)
                        },
                        modifier = Modifier.size(20.dp),
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
    /** Always-visible action beside the expand arrow (e.g. Generate). */
    trailingAction: (@Composable () -> Unit)? = null,
    /** In der Hülle (MapChrome): schwebendes Sheet-Menü statt alter Bottom-Bar (§4.2/J2). */
    chromeHosted: Boolean = false,
) {
    val dock: @Composable () -> Unit = {
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
                trailingAction?.invoke()
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
    if (chromeHosted) {
        // Neue Sheet-Optik in der Hülle: schwebende, abgerundete Fläche unten,
        // über der Karte und über dem MapChrome-DockBar — wie die Explore-Panels.
        // Der Boden-Abstand (DockBar-Oberkante liegt bei navigationBars + ~56dp;
        // 116dp) hält das Panel sauber ÜBER der DockBar (Bug: Planen/Gehen-Panel
        // wurde vom neuen Dock überdeckt/abgeschnitten).
        Box(modifier = Modifier.fillMaxWidth()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .padding(bottom = 116.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shadowElevation = 8.dp,
            ) {
                dock()
            }
        }
    } else {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 2.dp,
            shadowElevation = 6.dp,
        ) {
            dock()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuggestingMapLayout(
    uiState: RouteUiState,
    viewModel: RouteViewModel,
    modifier: Modifier = Modifier,
    chromeHosted: Boolean = false,
    section: RouteSection = RouteSection.FULL,
) {
    val mapPrefs = rememberMapPreferences()
    var selectedPreset by remember { mutableStateOf("normal") }
    // Frei-Tab (GO, nichts aktiv): Dock direkt aufgeklappt, damit man den
    // Free-Roam-Start sofort sieht statt hinter einem Chevron zu suchen.
    var dockLevel by remember {
        mutableStateOf(if (section == RouteSection.GO) DockLevel.EXPANDED else DockLevel.COLLAPSED)
    }
    // Bestätigung bevor man freies Gehen abbricht (Nr. 4b / Emil: zu leicht abzubrechen).
    var pendingFreeRoamStop by remember { mutableStateOf(false) }

    val nodeBadges = remember(uiState.startNodeId, uiState.destinationNodeId, uiState.isLoop, uiState.mustVisitNodeIds) {
        buildPlanningNodeBadges(uiState)
    }
    val segmentBadges = remember(uiState.requiredSegmentIds, uiState.excludedSegmentIds, uiState.segments) {
        buildPlanningSegmentBadges(uiState)
    }
    val summaryText = planningSummaryText(uiState)
    val fitKey = remember(uiState.startNodeId, uiState.destinationNodeId, uiState.mustVisitNodeIds, uiState.requiredSegmentIds, uiState.excludedSegmentIds) {
        "plan-${uiState.startNodeId}-${uiState.destinationNodeId}-${uiState.mustVisitNodeIds.joinToString(",")}-${uiState.requiredSegmentIds.joinToString(",")}"
    }

    Column(modifier = modifier.fillMaxSize()) {
        RouteMapChrome(
            uiState = uiState,
            mapStyle = mapPrefs.baseMapStyle,
            waymarkedOverlay = mapPrefs.waymarkedOverlay,
            routeGeometry = emptyList(),
            stations = emptyList(),
            goldenSegmentIds = emptySet(),
            disconnectedSegmentIds = uiState.disconnectedSegmentIds,
            fitKey = fitKey,
            fitToRouteOnly = false,
            emphasizeNetwork = true,
            planningMode = true,
            nodeBadges = nodeBadges,
            segmentBadges = segmentBadges,
            onMapClick = viewModel::onPlanningMapClick,
            modifier = Modifier.weight(1f),
            chromeHosted = chromeHosted,
        )

        RouteBottomDock(
            level = dockLevel,
            onToggleCollapse = {
                dockLevel = if (dockLevel == DockLevel.COLLAPSED) DockLevel.EXPANDED else DockLevel.COLLAPSED
            },
            chromeHosted = chromeHosted,
            summary = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f, fill = false)) {
                    if (uiState.freeRoamActive) {
                        Text(
                            stringResource(R.string.free_roam_active) + " · " + stringResource(R.string.free_roam_points_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (uiState.freeRoamReachedCount > 0) {
                            Text(
                                stringResource(R.string.free_roam_reached) + ": " + uiState.freeRoamReachedCount,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else if (section == RouteSection.GO) {
                        // Frei-Tab ohne aktive Route: kompakter Zustand — die volle
                        // Erklärung + Start-Button liegen im darunter liegenden primary.
                        Text(
                            stringResource(R.string.free_roam_title),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    } else {
                        Text(
                            summaryText,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val loading = uiState.status == RouteStatus.LOADING
                        val hasStart = uiState.startNodeId != null || uiState.homeNodeId != null
                        LengthPresetSelector(
                            selected = selectedPreset,
                            onSelect = { selectedPreset = it },
                            enabled = !loading && hasStart && !uiState.offlineCached,
                        )
                    }
                }
            },
            trailingAction = {
                // Frei-Tab ohne aktive Route: kein Go-Generieren-Button (Punkt 4).
                // Free-Roam braucht keine generierte Runde — der Start-Button liegt im primary.
                if (!uiState.freeRoamActive && section != RouteSection.GO) {
                    val loading = uiState.status == RouteStatus.LOADING
                    val hasStart = uiState.startNodeId != null || uiState.homeNodeId != null
                    val canGenerate = !loading && hasStart && !uiState.offlineCached
                    CompactButton(
                        onClick = {
                            viewModel.suggest(selectedPreset)
                            dockLevel = DockLevel.EXPANDED
                        },
                        enabled = canGenerate,
                    ) {
                        Text(stringResource(if (loading) R.string.route_generating else R.string.route_generate_arrow))
                    }
                }
            },
            primary = {
                // Free roam is its own state, NOT a guided route: no start/must-visit
                // planning, no station list — just your position as start and free
                // walking until you stop. Ab J3 nur im Gehen (GO/FULL), nie im Plan-Sheet.
                if (uiState.freeRoamActive && section != RouteSection.PLAN) {
                    Text(
                        stringResource(R.string.free_roam_active),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (uiState.freeRoamReachedCount > 0) {
                        Text(
                            stringResource(R.string.free_roam_reached) + ": " + uiState.freeRoamReachedCount,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    uiState.freeRoamAnnounce?.let {
                        if (it.isNotBlank()) {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                } else if (section == RouteSection.GO) {
                    val canStart = uiState.status != RouteStatus.LOADING
                    // GoSheet, nichts aktiv: Freies Gehen prominent starten.
                    Text(
                        stringResource(R.string.free_roam_start_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    CompactButton(onClick = viewModel::startFreeRoam, enabled = canStart) {
                        Text(stringResource(R.string.free_roam_start))
                    }
                } else {
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
                val loading = uiState.status == RouteStatus.LOADING
                if (uiState.freeRoamActive && section != RouteSection.PLAN) {
                    CompactButton(onClick = viewModel::finishFreeRoam, enabled = !loading) {
                        Text(stringResource(R.string.free_roam_finish))
                    }
                    CompactOutlinedButton(onClick = { pendingFreeRoamStop = true }, enabled = !loading) {
                        Text(stringResource(R.string.free_roam_stop))
                    }
                } else if (section == RouteSection.GO) {
                    // Gehen-Sheet: kein Planungs-Formular im erweiterten Bereich,
                    // wenn nichts aktiv ist.
                } else {
                    val hasStart = uiState.startNodeId != null || uiState.homeNodeId != null
                    val canGuide = !loading && hasStart && !uiState.offlineCached
                    CompactOutlinedButton(onClick = viewModel::surprise, enabled = canGuide) {
                        Text(stringResource(R.string.route_preset_surprise))
                    }
                    FavoritesLoadDeleteButton(uiState.favorites, loading, viewModel)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    val hasPlanning = uiState.destinationNodeId != null || uiState.mustVisitNodeIds.isNotEmpty() ||
                        uiState.requiredSegmentIds.isNotEmpty() || uiState.excludedSegmentIds.isNotEmpty()
                    if (hasPlanning) {
                        CompactOutlinedButton(onClick = viewModel::resetPlanning) {
                            Text(stringResource(R.string.route_node_menu_clear_all))
                        }
                    }
                }
            },
        )

        uiState.pendingDestinationReplace?.let { _ ->
            DestinationReplaceDialog(uiState, viewModel)
        }

        // Bestätigungsdialog beim Free-Roam-Abbruch (Nr. 4b): „Beenden“ schmeißt
        // sonst die laufende Aufzeichnung weg — erst fragen.
        if (pendingFreeRoamStop) {
            AlertDialog(
                onDismissRequest = { pendingFreeRoamStop = false },
                confirmButton = {
                    TextButton(onClick = {
                        pendingFreeRoamStop = false
                        viewModel.cancelFreeRoam()
                    }) {
                        Text(stringResource(R.string.free_roam_stop))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingFreeRoamStop = false }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                },
                text = { Text(stringResource(R.string.free_roam_stop_confirm)) },
            )
        }
    }
}

@Composable
private fun DestinationReplaceDialog(uiState: RouteUiState, viewModel: RouteViewModel) {
    val nextId = uiState.pendingDestinationReplace ?: return
    val nextNode = uiState.nodes.firstOrNull { it.id == nextId }
    AlertDialog(
        onDismissRequest = viewModel::dismissReplaceDestination,
        confirmButton = {
            TextButton(onClick = viewModel::confirmReplaceDestination) {
                Text(stringResource(R.string.route_node_second_destination_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::dismissReplaceDestination) {
                Text(stringResource(R.string.route_node_second_destination_cancel))
            }
        },
        title = { Text(stringResource(R.string.route_node_second_destination_title)) },
        text = {
            Text(
                stringResource(
                    R.string.route_node_second_destination_body,
                    nextNode?.name ?: "#$nextId",
                ),
            )
        },
    )
}
@Composable
private fun GuideNodeList(stations: List<RouteStation>, completedIndex: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        stations.forEachIndexed { index, station ->
            val done = index <= completedIndex
            val current = index == completedIndex + 1 || (completedIndex < 0 && index == 0)
            val label = station.name ?: "#${station.nodeId}"
            Text(
                text = "${index + 1}. $label",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    done -> MaterialTheme.colorScheme.primary
                    current -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
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
                CompactOutlinedButton(onClick = { viewModel.setNodeAsEnd(nodeId) }) {
                    val isCurrentDest = uiState.destinationNodeId == nodeId
                    Text(
                        if (isCurrentDest) "✓ " + stringResource(R.string.route_node_menu_end) else stringResource(R.string.route_node_menu_end),
                    )
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
internal fun planningSummaryText(uiState: RouteUiState): String {
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
internal fun nodeLabel(uiState: RouteUiState, nodeId: Int?): String {
    if (nodeId == null) return "…"
    val name = uiState.nodes.firstOrNull { it.id == nodeId }?.name ?: "#$nodeId"
    return if (nodeId == uiState.homeNodeId) "$name (${stringResource(R.string.map_node_home)})" else name
}

internal fun buildPlanningNodeBadges(uiState: RouteUiState): Map<Int, String> {
    val badges = mutableMapOf<Int, String>()
    uiState.startNodeId?.let { badges[it] = "S" }
    if (uiState.destinationNodeId != null) {
        uiState.destinationNodeId?.let { badges[it] = "Z" }
    }
    uiState.mustVisitNodeIds.forEachIndexed { index, id ->
        if (uiState.destinationNodeId != id) {
            badges[id] = if (uiState.mustVisitNodeIds.size > 1) "${index + 1}" else "★"
        }
    }
    return badges
}

internal fun buildPlanningSegmentBadges(uiState: RouteUiState): Map<Int, String> {
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
    chromeHosted: Boolean = false,
    section: RouteSection = RouteSection.FULL,
) {
    val context = LocalContext.current
    val mapPrefs = rememberMapPreferences()
    var dockLevel by remember { mutableStateOf(DockLevel.EXPANDED) }
    var hasLocationPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasLocationPermission = granted
        if (granted) viewModel.setFollowEnabled(true)
    }
    LaunchedEffect(uiState.mode) {
        if (uiState.mode == RouteMode.ACTIVE && !hasLocationPermission) {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else if (uiState.mode == RouteMode.ACTIVE && hasLocationPermission) {
            viewModel.setFollowEnabled(true)
        }
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
    var pendingBackDiscard by remember { mutableStateOf(false) }

    BackHandler(enabled = uiState.mode == RouteMode.ACTIVE) {
        pendingBackDiscard = true
    }

    if (pendingBackDiscard) {
        AlertDialog(
            onDismissRequest = { pendingBackDiscard = false },
            confirmButton = {
                TextButton(onClick = {
                    pendingBackDiscard = false
                    RouteTrackingForegroundService.stopAll(context)
                    viewModel.discardActive()
                }) {
                    Text(stringResource(R.string.route_discard_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingBackDiscard = false }) {
                    Text(stringResource(R.string.route_stay_on_route))
                }
            },
            text = { Text(stringResource(R.string.route_back_active_confirm)) },
        )
    }

    uiState.pendingDestinationReplace?.let { nextId ->
        DestinationReplaceDialog(uiState, viewModel)
    }

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

    val showLocation = uiState.mode == RouteMode.ACTIVE && hasLocationPermission
    val needsService = RouteTrackingForegroundService.needsForegroundService(uiState.trackEnabled, uiState.voiceEnabled)
    ActiveRouteLocationEffect(uiState, showLocation && !needsService, viewModel)
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

    val displayRouteGeometry = remember(route.geometry, route.stations, uiState.completedWaypointIndex, uiState.guideMode) {
        if (uiState.guideMode) {
            emptyList()
        } else if (uiState.completedWaypointIndex >= 0) {
            remainingRouteGeometry(route.geometry, route.stations, uiState.completedWaypointIndex)
        } else {
            route.geometry
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        RouteMapChrome(
            uiState = uiState,
            mapStyle = mapPrefs.baseMapStyle,
            waymarkedOverlay = mapPrefs.waymarkedOverlay,
            routeGeometry = displayRouteGeometry,
            stations = route.stations,
            goldenSegmentIds = uiState.goldenHitIds,
            goldenHitIds = uiState.goldenHitIds,
            disconnectedSegmentIds = uiState.disconnectedSegmentIds,
            fitKey = uiState.token.ifEmpty { route.nodeChain.joinToString("-") },
            fitToRouteOnly = true,
            emphasizeNetwork = false,
            completedWaypointIndex = uiState.completedWaypointIndex,
            trackedGeometry = uiState.trackedGeometry,
            followEnabled = showLocation && uiState.followEnabled,
            compassMode = uiState.compassMode,
            locationBearing = uiState.locationBearing,
            locationSpeed = uiState.locationSpeed,
            onCompassClick = if (showLocation) viewModel::cycleCompassMode else null,
            onMapLongClick = if (uiState.mode == RouteMode.ACTIVE) viewModel::onActiveMapLongPress else null,
            modifier = Modifier.weight(1f),
            chromeHosted = chromeHosted,
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

        uiState.repositionCandidates.takeIf { it.isNotEmpty() }?.let { candidates ->
            AlertDialog(
                onDismissRequest = viewModel::dismissRepositionPicker,
                title = { Text(stringResource(R.string.route_reposition_pick)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        candidates.forEach { nodeId ->
                            val node = uiState.nodes.find { it.id == nodeId }
                            TextButton(onClick = { viewModel.confirmReposition(nodeId) }) {
                                Text(node?.name ?: "#$nodeId")
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = viewModel::dismissRepositionPicker) {
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
            chromeHosted = chromeHosted,
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
                if (uiState.freeRoamActive) {
                    // Free roam is its own state here too: no guide list, no
                    // route point preview, no planning context — just your walk
                    // and reached junctions until you stop.
                    Text(
                        stringResource(R.string.free_roam_active),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (uiState.freeRoamReachedCount > 0) {
                        Text(
                            stringResource(R.string.free_roam_reached) + ": " + uiState.freeRoamReachedCount,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    uiState.freeRoamAnnounce?.let {
                        if (it.isNotBlank()) {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                } else {
                    if (uiState.guideMode) {
                        Text(
                            stringResource(R.string.route_guide_active_title),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        GuideNodeList(
                            stations = route.stations,
                            completedIndex = uiState.completedWaypointIndex,
                        )
                    } else if (stationPath.isNotBlank()) {
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
                        uiState.mode == RouteMode.SUGGESTING && uiState.pendingShareToken == null && section != RouteSection.GO -> {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (!uiState.guideMode) {
                                    CompactOutlinedButton(onClick = { viewModel.adjust("shorter") }, enabled = !loading) {
                                        Text(stringResource(R.string.route_shorter))
                                    }
                                    CompactOutlinedButton(onClick = viewModel::another, enabled = !loading) {
                                        Text(stringResource(R.string.route_again))
                                    }
                                    CompactOutlinedButton(onClick = { viewModel.adjust("longer") }, enabled = !loading) {
                                        Text(stringResource(R.string.route_longer))
                                    }
                                    if (uiState.token.isNotBlank()) {
                                        CompactOutlinedButton(onClick = viewModel::reverse, enabled = !loading) {
                                            Text(stringResource(R.string.route_reverse))
                                        }
                                    }
                                }
                                CompactButton(onClick = viewModel::select, enabled = !loading) {
                                    Text(stringResource(R.string.route_select))
                                }
                                CompactOutlinedButton(onClick = viewModel::cancel, enabled = !loading) {
                                    Text(stringResource(R.string.route_cancel))
                                }
                            }
                        }
                        uiState.mode == RouteMode.ACTIVE -> {
                            // Route ist übernommen (Auswählen), aber noch nicht gestartet:
                            // Hier liegt der eigentliche Start-Button „Go“, der erst jetzt
                            // das Tracking anschaltet (accept). So trackt nichts, bevor
                            // die Route auf der nächsten Seite bewusst bestätigt wurde.
                            if (!uiState.trackEnabled) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    CompactButton(
                                        onClick = viewModel::accept,
                                        enabled = !loading,
                                    ) {
                                        Text(stringResource(R.string.route_go))
                                    }
                                    CompactOutlinedButton(onClick = { pendingDiscard = true }) {
                                        Text(stringResource(R.string.route_discard_button))
                                    }
                                }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
                                CompactCheck(
                                    checked = uiState.voiceEnabled,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            ensureBackgroundPermissions { viewModel.setVoiceEnabled(true) }
                                        } else {
                                            viewModel.setVoiceEnabled(false)
                                        }
                                    },
                                    label = stringResource(R.string.route_voice_check),
                                )
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                val canComplete = !uiState.trackEnabled || uiState.completedWaypointIndex >= route.stations.lastIndex
                                CompactButton(
                                    onClick = {
                                        RouteTrackingForegroundService.stopAll(context)
                                        viewModel.complete()
                                    },
                                    enabled = canComplete,
                                ) {
                                    Text(stringResource(R.string.route_complete_button))
                                }
                                CompactOutlinedButton(onClick = { pendingDiscard = true }) {
                                    Text(stringResource(R.string.route_discard_button))
                                }
                                CompactOutlinedButton(onClick = viewModel::requestReposition) {
                                    Text(stringResource(R.string.route_reposition_node))
                                }
                                if (uiState.completedWaypointIndex < route.stations.lastIndex) {
                                    CompactOutlinedButton(
                                        onClick = {
                                            if (uiState.trackEnabled) {
                                                RouteTrackingForegroundService.markNextPassed(context)
                                            } else {
                                                viewModel.markNextStationPassed()
                                            }
                                        },
                                    ) {
                                        Text(stringResource(R.string.route_mark_passed))
                                    }
                                }
                            }
                            if (uiState.trackEnabled || uiState.voiceEnabled) {
                                BatteryOptimizationPrompt(modifier = Modifier.fillMaxWidth())
                            }
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
                    uiState.freeRoamActive -> {
                        CompactButton(onClick = viewModel::finishFreeRoam, enabled = !loading) {
                            Text(stringResource(R.string.free_roam_finish))
                        }
                        CompactOutlinedButton(onClick = viewModel::cancelFreeRoam, enabled = !loading) {
                            Text(stringResource(R.string.free_roam_stop))
                        }
                    }
                    uiState.mode == RouteMode.SUGGESTING && uiState.pendingShareToken == null && section != RouteSection.GO -> {
                        if (uiState.guideMode) {
                            Text(
                                stringResource(R.string.route_guide_points_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        CompactOutlinedButton(onClick = onStartRecording, enabled = !loading) {
                            Text(stringResource(R.string.record_entry_point))
                        }
                        uiState.pointPreview?.let { PointPreviewLines(it) }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text(
                            stringResource(R.string.free_roam_start_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        CompactButton(onClick = viewModel::startFreeRoam, enabled = !loading) {
                            Text(stringResource(R.string.free_roam_start))
                        }
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun FavoritesLoadDeleteButton(
    favorites: List<FavoriteEntry>,
    loading: Boolean,
    viewModel: RouteViewModel,
) {
    var open by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<FavoriteEntry?>(null) }

    CompactOutlinedButton(onClick = { open = true }) {
        Text(
            if (favorites.isEmpty()) {
                stringResource(R.string.route_favorites_title)
            } else {
                stringResource(R.string.route_favorites_title) + " (${favorites.size})"
            },
        )
    }

    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.route_favorites_title)) },
            text = {
                if (favorites.isEmpty()) {
                    Text(stringResource(R.string.route_favorites_empty))
                } else {
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
                if (uiState.completionGuideMode) {
                    Text(
                        stringResource(R.string.route_guide_completion_points),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
