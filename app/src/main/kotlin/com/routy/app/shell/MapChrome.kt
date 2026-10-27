package com.routy.app.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.routy.app.map.MapStyleSwitcher
import com.routy.app.map.MapViewModel
import com.routy.app.map.rememberMapPreferences
import com.routy.app.map.RoutyMapView
import com.routy.app.recording.RecordingBindingState
import com.routy.app.recording.RecordingPill
import com.routy.app.recording.RecordingViewModel
import com.routy.app.recording.rememberRecordingBinding
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
 * dauerhaft komponierten Sheets (Explore/Plan/Go/Record hosted). Moduswechsel disposed
 * nichts — die ViewModels leben am NavBackStackEntry der Shell (§4.2) und werden von
 * MapChrome wie von den hosted Screens via viewModel() aufgelöst (gleiche Instanz,
 * gleicher Owner). Telefon: Dock unten, Sheet unten. Tablet (≥600dp): Dock oben, Panel
 * rechts 400dp.
 *
 * J4: Aufnahme als vierter Modus (RecordSheet + Service-Bindung am Shell-Owner),
 * keine eigene Destination mehr. Pille zeigt während der Aufnahme über anderen Modi
 * den Weg zurück in die Aufnahme.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapChrome(
    shellViewModel: ShellViewModel,
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

    // --- Aufnahme: Service-Bindung + ViewModel am Shell-Owner (J4) ---
    val recordingViewModel: RecordingViewModel = viewModel(
        factory = viewModelFactory {
            initializer { RecordingViewModel(app.apiClientProvider, app.gpxCommitScheduler, app.recordingConfirmStore, app.bootstrapLoader) }
        },
    )
    val recordingUiState by recordingViewModel.uiState.collectAsState()
    val recordingBinding: RecordingBindingState = rememberRecordingBinding(recordingViewModel, recordingUiState.saved) {
        // Aufnahme abgeschlossen → zurück auf Erkunden, Sheet zu (Ankündigung §7.6).
        chrome = chrome.copy(mode = ShellMode.EXPLORE, stage = SheetStage.Peek)
    }
    val service = recordingBinding.service
    val serviceState = recordingBinding.serviceState
    val recordingPhase = serviceState.phase
    val recordingActive = recordingPhase == RecordingPhase.RECORDING ||
        recordingPhase == RecordingPhase.PAUSED ||
        recordingPhase == RecordingPhase.CONFIRM

    // Vierter Modus statt eigenem Nav-Ziel: Aufnahme öffnet als Sheet im Chrome.
    val startRecording: () -> Unit = { chrome = ChromeState(ShellMode.RECORD, SheetStage.Half, null) }

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
        recording = RecordingContext(
            phase = recordingPhase,
            myLocation = serviceState.currentPosition,
            points = if (recordingPhase == RecordingPhase.CONFIRM) {
                recordingUiState.points.map { GeoPoint(it.lat, it.lng) }
            } else {
                serviceState.trackGeometry
            },
        ),
    )

    val config = LocalConfiguration.current
    val isTablet = minOf(config.screenWidthDp, config.screenHeightDp) >= 600

    val routeActive = route != null && routeState.mode == RouteMode.ACTIVE
    val freeRoamActive = routeState.freeRoamActive

    // --- Profil/Stats/Settings/Admin als Stufen (§J2; Feinschliff J5) ---
    val shellState by shellViewModel.uiState.collectAsState()
    val baseUrl = app.secureStorage.serverUrl
    val token = app.secureStorage.token ?: ""
    // Ebenen-Panel oben rechts für Planen/Gehen (Erkunden hat seinen eigenen Hamburger).
    var layersOpen by remember { mutableStateOf(false) }

    // Back: muss VOR den Vollbild-Stufen (Stats/Settings/Web/Layers) komponiert sein, sonst
    // registriert kein BackHandler und Android-Back schließt die App (Bug: Back schloss in
    // Statistiken/Einstellungen die App). Live-Sessions übernimmt der hosted Screen
    // (RouteWithMapLayout hat seinen eigenen BackHandler für Discard/Bestätigung).
    BackHandler(enabled = !routeActive && !freeRoamActive) {
        // Ebenen-Panel hat Vorrang vor der Stufen-Logik: erst schließen.
        if (layersOpen) {
            layersOpen = false
            return@BackHandler
        }
        val result = onHardwareBack(
            state = chrome,
            webCanGoBack = false,
            routeActive = false,
            freeRoamActive = false,
            recordingPhase = recordingPhase,
            mapMode = mapState.mode.name,
        )
        when (result) {
            is BackResult.Apply -> {
                // Regel 6: Node/Segment → Half — dabei die Auswahl leeren, damit das
                // Panel wirklich schließt (nicht nur das Sheet einklappt).
                if (chrome.stage is SheetStage.Node || chrome.stage is SheetStage.Segment) {
                    mapViewModel.clearSelection()
                }
                chrome = ChromeState(result.mode ?: chrome.mode, result.stage, chrome.tool)
            }
            is BackResult.ClearMapMode -> {
                mapViewModel.setMode(MapMode.View)
                chrome = chrome.copy(stage = SheetStage.Half)
            }
            BackResult.ConfirmLeaveRoute -> routeViewModel.discardActive()
            BackResult.ConfirmStopFreeRoam -> routeViewModel.cancelFreeRoam()
            BackResult.Stay, BackResult.Pass, is BackResult.WebHistory -> Unit
        }
    }

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

    // §7.1: Sobald eine Live-Session läuft (ACTIVE oder freies Gehen), wechselt die Hülle
    // automatisch auf Gehen — Annehmen aus Planen kippt den Dock, Discard fällt zurück.
    LaunchedEffect(routeActive, freeRoamActive) {
        if ((routeActive || freeRoamActive) && mode != ShellMode.GO && mode != ShellMode.RECORD) {
            chrome = chrome.copy(mode = ShellMode.GO, stage = SheetStage.Peek)
        }
    }

    // §7.3/Regel 6: Die Erkunden-Knoten/-Segment-Panels sind eine Sheet-Stufe. Sobald im
    // Erkunden eine Auswahl aktiv ist, spiegelt chrome.stage das (Node/Segment) — nur so
    // greift onHardwareBack-Regel 6 (Back → Auswahl leeren + Half). Overlay-Stufen
    // (Profil/Stats/Settings/Web/EditMenu) werden nie überschrieben.
    LaunchedEffect(mode, chrome.stage, mapState.selectedNode, mapState.selectedSegment, mapState.mode) {
        val selection = when (mode) {
            ShellMode.EXPLORE -> when {
                mapState.selectedNode != null -> SheetStage.Node
                mapState.selectedSegment != null -> SheetStage.Segment
                else -> null
            }
            else -> null
        }
        val overlayStage =
            chrome.stage is SheetStage.Profile ||
                chrome.stage is SheetStage.Stats ||
                chrome.stage is SheetStage.Settings ||
                chrome.stage is SheetStage.Web ||
                chrome.stage is SheetStage.EditMenu
        if (selection != null && chrome.stage != selection && !overlayStage) {
            chrome = chrome.copy(stage = selection)
        } else if (selection == null && (chrome.stage is SheetStage.Node || chrome.stage is SheetStage.Segment)) {
            chrome = chrome.copy(stage = SheetStage.Half)
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
            onStartRecording = startRecording,
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (mode == ShellMode.EXPLORE) 1f else 0f)
                .zIndex(if (mode == ShellMode.EXPLORE) 2f else 0f),
        )
        PlanSheet(
            onStartRecording = startRecording,
            accountLocaleTag = accountLocaleTag,
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (mode == ShellMode.PLAN) 1f else 0f)
                .zIndex(if (mode == ShellMode.PLAN) 2f else 0f),
        )
        GoSheet(
            onStartRecording = startRecording,
            accountLocaleTag = accountLocaleTag,
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (mode == ShellMode.GO) 1f else 0f)
                .zIndex(if (mode == ShellMode.GO) 2f else 0f),
        )
        RecordSheet(
            uiState = recordingUiState,
            serviceState = serviceState,
            service = service,
            viewModel = recordingViewModel,
            onDone = { chrome = chrome.copy(mode = ShellMode.EXPLORE, stage = SheetStage.Peek) },
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (mode == ShellMode.RECORD) 1f else 0f)
                .zIndex(if (mode == ShellMode.RECORD) 2f else 0f),
        )

        // „Ebenen" für Planen/Gehen: oberer rechter Zugang (Erkunden hat seinen
        // eigenen Hamburger mit Ebenen-Panel im Extras-Menü). So kann man auch
        // während der Routenplanung / beim Gehen den Kartenstil wechseln.
        if (mode == ShellMode.PLAN || mode == ShellMode.GO) {
            IconButton(
                onClick = { layersOpen = !layersOpen },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .safeDrawingPadding()
                    .padding(top = 8.dp, end = 12.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                    .zIndex(12f),
            ) {
                Icon(Icons.Filled.Layers, contentDescription = stringResource(R.string.shell_layers))
            }
        }

        if (layersOpen) {
            Box(modifier = Modifier.fillMaxSize().zIndex(14f)) {
                val layersBoxModifier = if (isTablet) {
                    Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(top = 64.dp, end = 12.dp).width(360.dp)
                } else {
                    Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(top = 64.dp, end = 12.dp).width(280.dp)
                }
                Surface(
                    modifier = layersBoxModifier,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    shadowElevation = 8.dp,
                ) {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(stringResource(R.string.shell_layers), style = MaterialTheme.typography.titleSmall)
                        MapStyleSwitcher(
                            selected = mapPrefs.baseMapStyle,
                            onSelect = mapPrefs.setBaseMapStyle,
                            waymarkedOverlay = mapPrefs.waymarkedOverlay,
                            onWaymarkedOverlayChange = mapPrefs.setWaymarkedOverlay,
                        )
                    }
                }
            }
        }

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
                        ShellMode.RECORD -> startRecording()
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
                        // Aufnahme läuft im Hintergrund weiter (recordContinuesInBackground=true,
                        // §7.2) — die Pille führt zurück in die Aufnahme, kein Abbruch.
                        else -> {}
                    }
                }
            },
            onProfile = {
                if (mode != ShellMode.RECORD) chrome = chrome.copy(stage = SheetStage.Profile)
            },
            modifier = dockModifier.zIndex(10f),
        )

        // Pille: laufende Aufnahme ist über jeden Modus erreichbar (§J4).
        if (recordingActive && mode != ShellMode.RECORD) {
            RecordingPill(
                onOpen = startRecording,
                modifier = Modifier
                    .align(if (isTablet) Alignment.TopEnd else Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(if (isTablet) 16.dp else 8.dp)
                    .zIndex(12f),
            )
        }

        // Profil-Menü als volle Stufe (Stats/Settings/Admin/Logout, §J5)
        if (chrome.stage == SheetStage.Profile) {
            ProfileMenuSheet(
                displayName = shellState.user?.displayName ?: "",
                isAdmin = shellState.user?.role == "admin",
                scoreLine = rememberProfileScoreLine(),
                onStats = { chrome = chrome.copy(stage = SheetStage.Stats) },
                onSettings = { chrome = chrome.copy(stage = SheetStage.Settings) },
                onAccount = {
                    chrome = chrome.copy(stage = SheetStage.Web("/settings/account"))
                },
                onAdmin = { chrome = chrome.copy(stage = SheetStage.Web("/admin")) },
                onSignOut = shellViewModel::signOut,
                onDismiss = { chrome = chrome.copy(stage = SheetStage.Peek) },
            )
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
