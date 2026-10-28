package com.routy.app.shell

import com.routy.app.logic.api.GeoPoint
import com.routy.app.logic.api.NodeDto
import com.routy.app.logic.api.RouteDisplayPayload
import com.routy.app.logic.api.RouteStation
import com.routy.app.logic.api.SegmentDto
import com.routy.app.logic.recording.RecordingPhase
import com.routy.app.map.MapMode
import com.routy.app.route.MapCompassMode
import com.routy.app.route.RouteMode

/**
 * Pure Karten-Auswahl für die eine Hülle-Karte (§7.4, shell/MapBinding.kt).
 * Kein Compose — [com.routy.app.shell.MapBindingTest] läuft als JVM-Unit-Test.
 *
 * Regeln:
 * 1. Modus RECORD und Phase RECORDING/PAUSED/CONFIRM → Track "#9a3b29", myLocation aus dem Service, keine Klicks.
 * 2. Modus GO, oder (nur J2) PLAN und route != null → Route-Argumente (Stations, Follow, Kompass). Badges nur im Planen.
 * 3. Sonst → Netz (emphasizeNetwork), overlayLine/Edit-Vertices, onMapClick an MapViewModel.
 */
data class MapBindingArgs(
    val kind: MapBindingKind,
    val emphasizeNetworkSegments: Boolean = false,
    val routeGeometry: List<GeoPoint> = emptyList(),
    val stations: List<RouteStation> = emptyList(),
    val completedWaypointIndex: Int = -1,
    val myLocation: GeoPoint? = null,
    val followEnabled: Boolean = false,
    val compassMode: MapCompassMode = MapCompassMode.NORTH_FREE,
    val locationBearing: Float? = null,
    val locationSpeed: Float? = null,
    val trackedGeometry: List<GeoPoint> = emptyList(),
    val routeColor: String = "#2e6b49",
    val overlayLine: List<GeoPoint> = emptyList(),
    val editVertices: List<GeoPoint>? = null,
    val selectedEditVertexIndex: Int? = null,
    val nodeBadges: Map<Int, String> = emptyMap(),
    val segmentBadges: Map<Int, String> = emptyMap(),
    val onMapClick: Boolean = false,
    val onMapLongClick: Boolean = false,
    val fitToRouteOnly: Boolean = false,
    val fitKey: Any? = null,
)

enum class MapBindingKind { NETWORK, ROUTE, RECORD_TRACK }

private data class RecordingInfo(
    val phase: RecordingPhase,
    val myLocation: GeoPoint?,
    val points: List<GeoPoint>,
)

/** Aufnahme-Kontext, wie er aus Service/ViewModel kommt. */
data class RecordingContext(
    val phase: RecordingPhase = RecordingPhase.IDLE,
    val myLocation: GeoPoint? = null,
    val points: List<GeoPoint> = emptyList(),
)

data class ExploreMapContext(
    val mode: MapMode,
    val overlayLine: List<GeoPoint> = emptyList(),
    val editVertices: List<GeoPoint>? = null,
    val selectedEditVertexIndex: Int? = null,
    val onMapClick: Boolean = true,
)

data class RouteMapContext(
    val mode: RouteMode,
    val route: RouteDisplayPayload?,
    val routeGeometry: List<GeoPoint> = emptyList(),
    val stations: List<RouteStation> = emptyList(),
    val completedWaypointIndex: Int = -1,
    val myLocation: GeoPoint? = null,
    val followEnabled: Boolean = false,
    val compassMode: MapCompassMode = MapCompassMode.NORTH_FREE,
    val locationBearing: Float? = null,
    val locationSpeed: Float? = null,
    val trackedGeometry: List<GeoPoint> = emptyList(),
    val goldenHitIds: Set<Int> = emptySet(),
    val disconnectedSegmentIds: Set<Int> = emptySet(),
    val nodeBadges: Map<Int, String> = emptyMap(),
    val segmentBadges: Map<Int, String> = emptyMap(),
    val fitKey: Any? = null,
    /** M26: wahr = freies Gehen — Track-Zweig ohne Stationen, Strich in Aufnahme-Farbe (§6). */
    val freeRoam: Boolean = false,
)

/** Zentrale Einstiegspunkte, damit die pure Funktion keine ViewModels braucht. */
fun mapBindingFor(
    chromeMode: ShellMode,
    mapMode: MapMode,
    routeCtx: RouteMapContext?,
    explore: ExploreMapContext,
    recording: RecordingContext,
): MapBindingArgs {
    val liveRecording = recording.phase == RecordingPhase.RECORDING ||
        recording.phase == RecordingPhase.PAUSED ||
        recording.phase == RecordingPhase.CONFIRM

    // 1. Aufnahme-Track
    if (chromeMode == ShellMode.RECORD && liveRecording) {
        return MapBindingArgs(
            kind = MapBindingKind.RECORD_TRACK,
            routeColor = "#9a3b29",
            myLocation = recording.myLocation,
            routeGeometry = recording.points,
            stations = emptyList(),
            followEnabled = false,
            onMapClick = false,
            fitKey = "record-${recording.points.size}",
        )
    }

    // 2. Route (GO immer, PLAN nur in J2 wenn eine Route da ist)
    // routeCtx wird vom Aufrufer NUR gebaut, wenn eine aktive Route ODER freies
    // Gehen existiert — die Präsenz des Kontexts ist das Route-Signal.
    val showRoute = chromeMode == ShellMode.GO || (chromeMode == ShellMode.PLAN && routeCtx != null)
    if (showRoute && routeCtx != null) {
        val planning = chromeMode == ShellMode.PLAN
        return MapBindingArgs(
            kind = MapBindingKind.ROUTE,
            emphasizeNetworkSegments = false,
            routeGeometry = routeCtx.routeGeometry,
            stations = routeCtx.stations,
            completedWaypointIndex = routeCtx.completedWaypointIndex,
            myLocation = routeCtx.myLocation,
            followEnabled = routeCtx.followEnabled,
            compassMode = routeCtx.compassMode,
            locationBearing = routeCtx.locationBearing,
            locationSpeed = routeCtx.locationSpeed,
            trackedGeometry = routeCtx.trackedGeometry,
            // M26: freies Gehen zeichnet seinen gelaufenen Strich in der Aufnahme-Farbe
            // §6 (#9a3b29), die generierte Route bleibt Akzent (M41 fasst den Akzent an).
            routeColor = if (routeCtx.freeRoam) "#9a3b29" else "#2e6b49",
            nodeBadges = if (planning && !routeCtx.freeRoam) routeCtx.nodeBadges else emptyMap(),
            segmentBadges = if (planning && !routeCtx.freeRoam) routeCtx.segmentBadges else emptyMap(),
            onMapClick = planning,
            onMapLongClick = routeCtx.mode == RouteMode.ACTIVE,
            fitToRouteOnly = true,
            fitKey = routeCtx.fitKey,
        )
    }

    // 3. Netz (Erkunden / Werkzeuge)
    return MapBindingArgs(
        kind = MapBindingKind.NETWORK,
        emphasizeNetworkSegments = true,
        overlayLine = explore.overlayLine,
        editVertices = explore.editVertices,
        selectedEditVertexIndex = explore.selectedEditVertexIndex,
        onMapClick = explore.onMapClick,
        fitToRouteOnly = false,
        fitKey = "network",
    )
}
