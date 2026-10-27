package com.routy.app.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.Arrangement
import com.routy.app.R
import com.routy.app.RoutyApplication
import com.routy.app.logic.api.GeoPoint
import com.routy.app.logic.recording.RecordingPhase
import com.routy.app.logic.route.remainingRouteGeometry
import com.routy.app.map.MapMode
import com.routy.app.map.MapViewModel
import com.routy.app.map.rememberMapPreferences
import com.routy.app.map.RoutyMapView
import com.routy.app.route.RouteMode
import com.routy.app.route.RouteViewModel
import com.routy.app.route.buildPlanningNodeBadges
import com.routy.app.route.buildPlanningSegmentBadges
import com.routy.app.settings.SettingsScreen
import com.routy.app.stats.StatsScreen
import com.routy.app.webview.AuthenticatedWebSheet
import com.routy.app.webview.ShellViewModel

/**
 * Atlas-Hülle auf Android (§4.2): genau EINE RoutyMapView, DockBar, Flächen, und die
 * dauerhaft komponierten Sheets (Explore/Plan hosted). Moduswechsel disposed nichts —
 * die ViewModels leben am NavBackStackEntry der Shell (§4.2) und werden von MapChrome
 * wie von den hosted Screens via viewModel() aufgelöst (gleiche Instanz, gleicher Owner).
 * Telefon: Dock unten, Sheet unten. Tablet (≥600dp): Dock oben, Panel rechts 400dp.
 *
 * J2: Gehen-Chip disabled (DockBar), Aufnahme-Chip öffnet die alte RecordingScreen
 * (separate Destination — Review-Notiz). Die aktive Route liegt noch im Plan-Sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapChrome(
    shellViewModel: ShellViewModel,
    onStartRecording: () -> Unit,
    accountLocaleTag: String,
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.applicationContext as RoutyApplication
    val routeViewModel: RouteViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                RouteViewModel(app.apiClientProvider, app.routeProgressStore, app.routeWalkTrackStore, app.networkCache, app.bootstrapLoader, app.mapTilePrefetchScheduler)
            }
        },
    )
    val mapViewModel: MapViewModel = viewModel(
        factory = viewModelFactory {
            initializer { MapViewModel(app.networkCache, app.bootstrapLoader, app.apiClientProvider) }
        },
    )
    val routeState by routeViewModel.uiState.collectAsState()
    val mapState by mapViewModel.uiState.collectAsState()
    val mapPrefs = rememberMapPreferences()

    var chrome by rememberSaveable(stateSaver = ChromeStateSaver) {
        mutableStateOf(ChromeState())
    }
    val mode = chrome.mode

    // --- Eine Karten-Instanz, gefüttert von MapBinding (§7.4) ---
    val route = routeState.route
    val displayRouteGeometry = remember(route?.geometry, route?.stations, routeState.completedWaypointIndex, routeState.guideMode) {
        when {
            route == null -> emptyList()
            routeState.guideMode -> emptyList()
            routeState.completedWaypointIndex >= 0 -> remainingRouteGeometry(route.geometry, route.stations, routeState.completedWaypointIndex)
            else -> route.geometry
        }
    }

    val routeCtx = if (route != null && (mode == ShellMode.PLAN || mode == ShellMode.GO)) {
        RouteMapContext(
            mode = routeState.mode,
            route = route,
            routeGeometry = displayRouteGeometry,
            stations = route.stations,
            completedWaypointIndex = routeState.completedWaypointIndex,
            myLocation = routeState.myLocation,
            followEnabled = routeState.mode == RouteMode.ACTIVE && routeState.followEnabled,
            compassMode = routeState.compassMode,
            locationBearing = routeState.locationBearing,
            locationSpeed = routeState.locationSpeed,
            trackedGeometry = routeState.trackedGeometry,
            goldenHitIds = routeState.goldenHitIds,
            disconnectedSegmentIds = routeState.disconnectedSegmentIds,
            nodeBadges = if (mode == ShellMode.PLAN) buildPlanningNodeBadges(routeState) else emptyMap(),
            segmentBadges = if (mode == ShellMode.PLAN) buildPlanningSegmentBadges(routeState) else emptyMap(),
            fitKey = if (routeState.token.isEmpty()) route.nodeChain.joinToString("-") else routeState.token,
        )
    } else null

    val overlayLine = when (mapState.mode) {
        MapMode.Draw -> mapState.drawPoints.map { GeoPoint(it.lat, it.lng) }
        MapMode.EditSegment -> mapState.editSegmentPoints.orEmpty()
        else -> emptyList()
    }
    val explore = ExploreMapContext(
        mode = mapState.mode,
        overlayLine = overlayLine,
        editVertices = if (mapState.mode == MapMode.EditSegment) mapState.editSegmentPoints else null,
        selectedEditVertexIndex = mapState.selectedEditVertexIndex,
        onMapClick = mapState.mode != MapMode.Gpx,
    )

    val binding = mapBindingFor(
        chromeMode = mode,
        mapMode = mapState.mode,
        routeCtx = routeCtx,
        explore = explore,
        recording = RecordingContext(RecordingPhase.IDLE), // J2: Aufnahme separate Destination
    )

    val config = LocalConfiguration.current
    val isTablet = minOf(config.screenWidthDp, config.screenHeightDp) >= 600

    val routeActive = route != null && routeState.mode == RouteMode.ACTIVE
    val freeRoamActive = routeState.freeRoamActive

    // --- Profil/Stats/Settings/Admin als Stufen (§J2; Feinschliff J5) ---
    val shellState by shellViewModel.uiState.collectAsState()
    val baseUrl = app.secureStorage.serverUrl
    val token = app.secureStorage.token ?: ""

    if (chrome.stage == SheetStage.Stats) {
        Box(modifier.fillMaxSize().zIndex(20f)) {
            StatsScreen(modifier = Modifier.fillMaxSize())
        }
        return
    }
    if (chrome.stage == SheetStage.Settings) {
        Box(modifier.fillMaxSize().zIndex(20f)) {
            SettingsScreen(
                onSignOut = shellViewModel::signOut,
                modifier = Modifier.fillMaxSize(),
            )
        }
        return
    }
    if (chrome.stage is SheetStage.Web && baseUrl != null && token.isNotEmpty()) {
        AuthenticatedWebSheet(
            baseUrl = baseUrl,
            token = token,
            path = (chrome.stage as SheetStage.Web).path.takeIf { it.isNotBlank() } ?: "/admin",
            onDismiss = { chrome = chrome.copy(stage = SheetStage.Profile) },
            onNavigateToLogin = shellViewModel::signOut,
        )
        return
    }

    // Back: bei laufender Live-Session übernimmt der hosted Screen (RouteWithMapLayout
    // hat seinen eigenen BackHandler für Discard/Bestätigung); sonst Stufen-Logik der Hülle.
    BackHandler(enabled = !routeActive && !freeRoamActive) {
        val result = onHardwareBack(
            state = chrome,
            webCanGoBack = false,
            routeActive = false,
            freeRoamActive = false,
            recordingPhase = RecordingPhase.IDLE,
            mapMode = mapState.mode.name,
        )
        when (result) {
            is BackResult.Apply -> chrome = ChromeState(result.mode ?: chrome.mode, result.stage, chrome.tool)
            is BackResult.ClearMapMode -> {
                mapViewModel.setMode(MapMode.View)
                chrome = chrome.copy(stage = SheetStage.Half)
            }
            BackResult.ConfirmLeaveRoute -> routeViewModel.discardActive()
            BackResult.ConfirmStopFreeRoam -> routeViewModel.cancelFreeRoam()
            BackResult.Stay, BackResult.Pass, is BackResult.WebHistory -> Unit
        }
    }

    // §7.1: Sobald eine Live-Session läuft (ACTIVE oder freies Gehen), wechselt die Hülle
    // automatisch auf Gehen — Annehmen aus Planen kippt den Dock, Discard fällt zurück.
    LaunchedEffect(routeActive, freeRoamActive) {
        if ((routeActive || freeRoamActive) && mode != ShellMode.GO && mode != ShellMode.RECORD) {
            chrome = chrome.copy(mode = ShellMode.GO, stage = SheetStage.Peek)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        RoutyMapView(
            style = mapPrefs.baseMapStyle,
            waymarkedOverlay = mapPrefs.waymarkedOverlay,
            nodes = if (binding.kind == MapBindingKind.NETWORK) mapState.nodes else routeState.nodes,
            segments = if (binding.kind == MapBindingKind.NETWORK) mapState.segments else routeState.segments,
            routeGeometry = when (binding.kind) {
                MapBindingKind.ROUTE, MapBindingKind.RECORD_TRACK -> binding.routeGeometry
                MapBindingKind.NETWORK -> binding.overlayLine
            },
            stations = binding.stations,
            myLocation = binding.myLocation,
            fitKey = binding.fitKey
                ?: (if (binding.kind == MapBindingKind.NETWORK) mapState.nodes.size else routeState.nodes.size),
            fitToRouteOnly = binding.fitToRouteOnly,
            emphasizeNetworkSegments = binding.emphasizeNetworkSegments,
            completedWaypointIndex = binding.completedWaypointIndex,
            trackedGeometry = binding.trackedGeometry,
            followEnabled = binding.followEnabled,
            compassMode = binding.compassMode,
            locationBearing = binding.locationBearing,
            locationSpeed = binding.locationSpeed,
            goldenSegmentIds = routeState.goldenHitIds,
            disconnectedSegmentIds = routeState.disconnectedSegmentIds,
            homeNodeId = routeState.homeNodeId,
            startNodeId = when {
                routeState.freeRoamActive -> routeState.freeRoamStartNodeId
                binding.kind == MapBindingKind.ROUTE && mode == ShellMode.PLAN -> routeState.startNodeId
                else -> null
            },
            endNodeId = if (binding.kind == MapBindingKind.ROUTE && mode == ShellMode.PLAN) routeState.destinationNodeId else null,
            mustVisitNodeIds = if (binding.kind == MapBindingKind.ROUTE && mode == ShellMode.PLAN) routeState.mustVisitNodeIds else emptyList(),
            isLoop = routeState.isLoop,
            requiredSegmentIds = if (binding.kind == MapBindingKind.ROUTE && mode == ShellMode.PLAN) routeState.requiredSegmentIds.toSet() else emptySet(),
            excludedSegmentIds = if (binding.kind == MapBindingKind.ROUTE && mode == ShellMode.PLAN) routeState.excludedSegmentIds.toSet() else emptySet(),
            selectedNodeId = if (binding.kind == MapBindingKind.NETWORK) mapState.selectedNode?.id else null,
            selectedSegmentId = if (binding.kind == MapBindingKind.NETWORK) mapState.selectedSegment?.id else null,
            nodeBadges = binding.nodeBadges,
            segmentBadges = binding.segmentBadges,
            overlayLine = if (binding.kind == MapBindingKind.NETWORK) binding.overlayLine else emptyList(),
            editVertices = if (binding.kind == MapBindingKind.NETWORK) binding.editVertices else null,
            selectedEditVertexIndex = if (binding.kind == MapBindingKind.NETWORK) binding.selectedEditVertexIndex else null,
            onMapClick = if (binding.onMapClick) mapViewModel::onMapClick else null,
            onMapLongClick = if (binding.onMapLongClick) routeViewModel::onActiveMapLongPress else null,
            routeColor = binding.routeColor,
            contentPadding = PaddingValues(
                start = 0.dp,
                top = if (isTablet) (48 + 64).dp else 16.dp,
                end = if (isTablet) 416.dp else 16.dp,
                bottom = if (isTablet) 16.dp else (56 + 96).dp,
            ),
            modifier = Modifier.fillMaxSize(),
        )

        // Sheets: immer komponiert, das aktive sichtbar (§4.3 — ViewModels überleben den Moduswechsel)
        ExploreSheet(
            onStartRecording = onStartRecording,
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (mode == ShellMode.EXPLORE) 1f else 0f)
                .zIndex(if (mode == ShellMode.EXPLORE) 2f else 0f),
        )
        PlanSheet(
            onStartRecording = onStartRecording,
            accountLocaleTag = accountLocaleTag,
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (mode == ShellMode.PLAN) 1f else 0f)
                .zIndex(if (mode == ShellMode.PLAN) 2f else 0f),
        )
        GoSheet(
            onStartRecording = onStartRecording,
            accountLocaleTag = accountLocaleTag,
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (mode == ShellMode.GO) 1f else 0f)
                .zIndex(if (mode == ShellMode.GO) 2f else 0f),
        )

        // Dock + Profil
        val dockModifier = if (isTablet) {
            Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 8.dp)
        } else {
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 8.dp)
        }
        DockBar(
            mode = mode,
            onSelect = { next ->
                val live = when {
                    routeActive -> LiveSession.ActiveRoute
                    freeRoamActive -> LiveSession.FreeRoam
                    else -> LiveSession.None
                }
                val decision = requestModeChange(mode, next, live, recordContinuesInBackground = true)
                if (decision is ModeChangeResult.Allow) {
                    when (next) {
                        ShellMode.RECORD -> onStartRecording()
                        else -> chrome = ChromeState(next, SheetStage.Half, null)
                    }
                } else if (decision is ModeChangeResult.Confirm) {
                    when (decision.kind) {
                        "stop-route" -> {
                            routeViewModel.discardActive()
                            chrome = ChromeState(next, SheetStage.Half, null)
                        }
                        "stop-roam" -> {
                            routeViewModel.cancelFreeRoam()
                            chrome = ChromeState(next, SheetStage.Half, null)
                        }
                        else -> {}
                    }
                }
            },
            onProfile = {
                if (mode != ShellMode.RECORD) chrome = chrome.copy(stage = SheetStage.Profile)
            },
            modifier = dockModifier.zIndex(10f),
        )

        // Profil-Menü als volle Stufe (Stats/Settings/Admin/Logout, §J2)
        if (chrome.stage == SheetStage.Profile) {
            androidx.compose.material3.ModalBottomSheet(
                onDismissRequest = { chrome = chrome.copy(stage = SheetStage.Peek) },
            ) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                ) {
                    androidx.compose.material3.Text(
                        shellState.user?.displayName ?: "",
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    )
                    androidx.compose.material3.Button(
                        onClick = { chrome = chrome.copy(stage = SheetStage.Stats) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { androidx.compose.material3.Text(stringResource(R.string.nav_stats)) }
                    androidx.compose.material3.Button(
                        onClick = { chrome = chrome.copy(stage = SheetStage.Settings) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { androidx.compose.material3.Text(stringResource(R.string.nav_settings)) }
                    if (shellState.user?.role == "admin") {
                        androidx.compose.material3.Button(
                            onClick = { chrome = chrome.copy(stage = SheetStage.Web("/admin")) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { androidx.compose.material3.Text(stringResource(R.string.nav_admin)) }
                    }
                    androidx.compose.material3.OutlinedButton(
                        onClick = shellViewModel::signOut,
                        modifier = Modifier.fillMaxWidth(),
                    ) { androidx.compose.material3.Text(stringResource(R.string.settings_logout)) }
                }
            }
        }
    }
}

/** Sealed SheetStage → String (ChromeState-Saver; sheet-stage sind keine Enum-Werte). */
private fun stageKey(stage: SheetStage): String = when (stage) {
    SheetStage.Hidden -> "hidden"
    SheetStage.Peek -> "peek"
    SheetStage.Half -> "half"
    SheetStage.Expanded -> "expanded"
    SheetStage.Profile -> "profile"
    SheetStage.Stats -> "stats"
    SheetStage.Settings -> "settings"
    is SheetStage.Web -> "web"
    SheetStage.EditMenu -> "editMenu"
    SheetStage.Node -> "node"
    SheetStage.Segment -> "segment"
}

private fun stageFromKey(key: String): SheetStage = when (key) {
    "peek" -> SheetStage.Peek
    "half" -> SheetStage.Half
    "expanded" -> SheetStage.Expanded
    "profile" -> SheetStage.Profile
    "stats" -> SheetStage.Stats
    "settings" -> SheetStage.Settings
    "web" -> SheetStage.Web("")
    "editMenu" -> SheetStage.EditMenu
    "node" -> SheetStage.Node
    "segment" -> SheetStage.Segment
    else -> SheetStage.Hidden
}

/** ChromeState als rememberSaveable aus drei Strings (Modus, Stufe, Werkzeug) — §J2. */
private val ChromeStateSaver = listSaver<ChromeState, Any>(
    save = {
        listOf(it.mode.name, stageKey(it.stage), it.tool ?: "")
    },
    restore = { list ->
        ChromeState(
            mode = runCatching { ShellMode.valueOf(list[0] as String) }.getOrDefault(ShellMode.EXPLORE),
            stage = stageFromKey(list[1] as String),
            tool = (list[2] as String).takeIf { it.isNotEmpty() },
        )
    },
)
