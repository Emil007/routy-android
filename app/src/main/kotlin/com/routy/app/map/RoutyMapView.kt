package com.routy.app.map

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.gson.JsonObject
import com.routy.app.logic.api.GeoPoint
import com.routy.app.logic.api.NodeDto
import com.routy.app.logic.api.RouteStation
import com.routy.app.logic.api.SegmentDto
import com.routy.app.logic.api.isCanonical
import com.routy.app.logic.api.isLocked
import com.routy.app.route.MapCompassMode
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** Bundled as local style JSON assets (app/src/main/assets/styles/) rather than resolved via
 * Style.Builder().fromJson() at runtime — same raster tile sources the web app's MapView.tsx
 * offers (TILE_LAYERS), just baked into a minimal MapLibre style doc each since raw XYZ tile
 * URLs need a "sources"/"layers" wrapper to be a valid style, unlike Leaflet's TileLayer. */
enum class BaseMapStyle(val assetUri: String) {
    STREETS("asset://styles/streets.json"),
    HIKING("asset://styles/hiking.json"),
    SATELLITE("asset://styles/satellite.json"),
}

private const val WAYMARKED_SOURCE = "routy-waymarked"
private const val WAYMARKED_LAYER = "routy-waymarked-layer"
private const val WAYMARKED_TILE_URL = "https://tile.waymarkedtrails.org/hiking/{z}/{x}/{y}.png"

private const val SEGMENTS_SOURCE = "routy-segments"
private const val SEGMENTS_LAYER = "routy-segments-layer"
private const val ROUTE_SOURCE = "routy-route"
private const val ROUTE_LAYER = "routy-route-layer"
private const val NODES_SOURCE = "routy-nodes"
private const val NODES_LAYER = "routy-nodes-layer"
private const val STATIONS_SOURCE = "routy-stations"
private const val STATIONS_LAYER = "routy-stations-layer"
private const val OVERLAY_SOURCE = "routy-overlay"
private const val OVERLAY_LAYER = "routy-overlay-layer"
private const val HIGHLIGHT_SOURCE = "routy-segment-highlight"
private const val HIGHLIGHT_LAYER = "routy-segment-highlight-layer"
private const val GOLDEN_SEGMENTS_SOURCE = "routy-segments-golden"
private const val GOLDEN_SEGMENTS_LAYER = "routy-segments-golden-layer"
private const val GOLDEN_HIT_SOURCE = "routy-segments-golden-hit"
private const val GOLDEN_HIT_LAYER = "routy-segments-golden-hit-layer"
private const val LOCKED_SEGMENTS_SOURCE = "routy-segments-locked"
private const val LOCKED_SEGMENTS_LAYER = "routy-segments-locked-layer"
private const val EDIT_VERTICES_SOURCE = "routy-edit-vertices"
private const val EDIT_VERTICES_LAYER = "routy-edit-vertices-layer"
private const val TRACKED_SOURCE = "routy-tracked"
private const val TRACKED_LAYER = "routy-tracked-layer"
private const val ME_SOURCE = "routy-me"
private const val ME_LAYER = "routy-me-layer"
private const val NODE_BADGES_SOURCE = "routy-node-badges"
private const val NODE_BADGES_LAYER = "routy-node-badges-layer"
private const val SEGMENT_BADGES_SOURCE = "routy-segment-badges"
private const val SEGMENT_BADGES_LAYER = "routy-segment-badges-layer"

/**
 * Read-only network + route display: every known segment drawn faint in the background, the
 * active/suggested route highlighted on top of it, network nodes as small dots, the current
 * route's stations as bigger accent-colored dots, and an optional live position marker.
 * Network editing (rename, move, draw, GPX import, segment geometry) is native on the Map tab;
 * this view renders the graph and forwards taps to MapViewModel.
 */
@Composable
fun RoutyMapView(
    style: BaseMapStyle,
    waymarkedOverlay: Boolean = false,
    nodes: List<NodeDto>,
    segments: List<SegmentDto>,
    routeGeometry: List<GeoPoint>,
    stations: List<RouteStation>,
    myLocation: GeoPoint?,
    fitKey: Any?,
    /** When >= 0, stations at or below this index render as completed; the next station is highlighted. */
    completedWaypointIndex: Int = -1,
    goldenSegmentIds: Set<Int> = emptySet(),
    /** Subset of [goldenSegmentIds] that are on the active/suggested route — drawn thicker. */
    goldenHitIds: Set<Int> = emptySet(),
    /** Canonical segments not reachable from home — drawn red on route map. */
    disconnectedSegmentIds: Set<Int> = emptySet(),
    /** GPS trace recorded while Track is on (completed hops). */
    trackedGeometry: List<GeoPoint> = emptyList(),
    followEnabled: Boolean = false,
    compassMode: MapCompassMode = MapCompassMode.NORTH_FREE,
    locationBearing: Float? = null,
    locationSpeed: Float? = null,
    selectedNodeId: Int? = null,
    selectedSegmentId: Int? = null,
    moveNodeId: Int? = null,
    homeNodeId: Int? = null,
    startNodeId: Int? = null,
    endNodeId: Int? = null,
    mustVisitNodeIds: List<Int> = emptyList(),
    isLoop: Boolean = true,
    requiredSegmentIds: Set<Int> = emptySet(),
    excludedSegmentIds: Set<Int> = emptySet(),
    nodeBadges: Map<Int, String> = emptyMap(),
    /** M1: R/E badges with segment names at path midpoints during route planning. */
    segmentBadges: Map<Int, String> = emptyMap(),
    overlayLine: List<GeoPoint> = emptyList(),
    editVertices: List<GeoPoint>? = null,
    selectedEditVertexIndex: Int? = null,
    onMapClick: ((lat: Double, lng: Double) -> Unit)? = null,
    onMapLongClick: ((lat: Double, lng: Double) -> Unit)? = null,
    modifier: Modifier = Modifier,
    /** When true (default), camera fits route geometry + stations (+ myLocation), not the whole network. */
    fitToRouteOnly: Boolean = true,
    /** "#2e6b49" (brand green) for an active/suggested route; the recording screen passes a
     * distinct reddish-brown ("#9a3b29") for a track being recorded, matching MapView.tsx's
     * own color choice for the same distinction (RecordTrackWizard.tsx's trackLine). */
    routeColor: String = "#2e6b49",
    /** Strong green strokes for the map overview tab; faint grey when a route is drawn on top. */
    emphasizeNetworkSegments: Boolean = false,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember { MapView(context) }
    var maplibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var loadedStyle by remember { mutableStateOf<Style?>(null) }
    val onMapClickState by rememberUpdatedState(onMapClick)
    val onMapLongClickState by rememberUpdatedState(onMapLongClick)

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    AndroidView(modifier = modifier.fillMaxSize(), factory = { mapView })

    LaunchedEffect(mapView) {
        mapView.getMapAsync { map ->
            maplibreMap = map
            map.addOnMapClickListener { point ->
                onMapClickState?.invoke(point.latitude, point.longitude)
                true
            }
            map.addOnMapLongClickListener { point ->
                onMapLongClickState?.invoke(point.latitude, point.longitude)
                true
            }
        }
    }

    LaunchedEffect(maplibreMap, style) {
        val map = maplibreMap ?: return@LaunchedEffect
        loadedStyle = null
        map.setStyle(Style.Builder().fromUri(style.assetUri)) { newStyle -> loadedStyle = newStyle }
    }

    LaunchedEffect(loadedStyle, nodes, segments, routeGeometry, stations, myLocation, trackedGeometry, routeColor, completedWaypointIndex, goldenSegmentIds, goldenHitIds, disconnectedSegmentIds, selectedNodeId, moveNodeId, homeNodeId, startNodeId, endNodeId, mustVisitNodeIds, isLoop, requiredSegmentIds, excludedSegmentIds, nodeBadges, segmentBadges, selectedSegmentId, overlayLine, editVertices, selectedEditVertexIndex, emphasizeNetworkSegments, waymarkedOverlay) {
        val currentStyle = loadedStyle ?: return@LaunchedEffect
        updateWaymarkedOverlay(currentStyle, waymarkedOverlay)
        updateSegmentsLayer(currentStyle, segments, emphasizeNetworkSegments, requiredSegmentIds, excludedSegmentIds, goldenSegmentIds, disconnectedSegmentIds)
        updateGoldenSegmentsLayer(currentStyle, segments, goldenSegmentIds, goldenHitIds)
        updateSelectedSegmentLayer(currentStyle, segments, selectedSegmentId)
        updateOverlayLayer(currentStyle, overlayLine)
        updateEditVerticesLayer(currentStyle, editVertices, selectedEditVertexIndex)
        updateRouteLayer(currentStyle, routeGeometry, routeColor)
        updateTrackedLayer(currentStyle, trackedGeometry)
        updateNodesLayer(currentStyle, nodes, selectedNodeId, moveNodeId, homeNodeId, startNodeId, endNodeId, mustVisitNodeIds, isLoop)
        updateNodeBadgesLayer(currentStyle, nodes, nodeBadges)
        updateSegmentBadgesLayer(currentStyle, segments, segmentBadges)
        updateStationsLayer(currentStyle, stations, completedWaypointIndex)
        updateMyLocationLayer(currentStyle, myLocation)
    }

    LaunchedEffect(maplibreMap, followEnabled, myLocation, compassMode, locationBearing, locationSpeed) {
        val map = maplibreMap ?: return@LaunchedEffect
        if (!followEnabled || myLocation == null) return@LaunchedEffect
        val ui = map.uiSettings
        val heading = when {
            (locationSpeed ?: 0f) >= 1f && locationBearing != null -> locationBearing.toDouble()
            locationBearing != null -> locationBearing.toDouble()
            else -> 0.0
        }
        when (compassMode) {
            MapCompassMode.NORTH_FREE -> {
                ui.isRotateGesturesEnabled = true
                map.animateCamera(CameraUpdateFactory.newLatLng(LatLng(myLocation.lat, myLocation.lng)), 400)
            }
            MapCompassMode.HEADING_UP -> {
                ui.isRotateGesturesEnabled = false
                val pos = CameraPosition.Builder()
                    .target(LatLng(myLocation.lat, myLocation.lng))
                    .zoom(map.cameraPosition.zoom.coerceAtLeast(15.0))
                    .bearing(heading)
                    .tilt(0.0)
                    .build()
                map.animateCamera(CameraUpdateFactory.newCameraPosition(pos), 400)
            }
            MapCompassMode.NORTH_LOCKED -> {
                ui.isRotateGesturesEnabled = true
                val pos = CameraPosition.Builder()
                    .target(LatLng(myLocation.lat, myLocation.lng))
                    .zoom(map.cameraPosition.zoom.coerceAtLeast(15.0))
                    .bearing(0.0)
                    .tilt(0.0)
                    .build()
                map.animateCamera(CameraUpdateFactory.newCameraPosition(pos), 400)
            }
        }
    }

    LaunchedEffect(loadedStyle, fitKey, fitToRouteOnly) {
        val map = maplibreMap ?: return@LaunchedEffect
        if (loadedStyle == null) return@LaunchedEffect
        val points = buildList {
            if (fitToRouteOnly) {
                routeGeometry.forEach { add(LatLng(it.lat, it.lng)) }
                stations.forEach { add(LatLng(it.lat, it.lng)) }
                myLocation?.let { add(LatLng(it.lat, it.lng)) }
            } else {
                nodes.forEach { add(LatLng(it.lat, it.lng)) }
                routeGeometry.forEach { add(LatLng(it.lat, it.lng)) }
            }
        }
        when {
            points.isEmpty() -> {}
            points.size == 1 -> map.moveCamera(CameraUpdateFactory.newLatLngZoom(points[0], 15.0))
            else -> map.moveCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(points).build(), 64))
        }
    }
}

private fun updateWaymarkedOverlay(style: Style, enabled: Boolean) {
    if (!enabled) {
        if (style.getLayer(WAYMARKED_LAYER) != null) {
            style.removeLayer(WAYMARKED_LAYER)
            style.removeSource(WAYMARKED_SOURCE)
        }
        return
    }
    if (style.getLayer(WAYMARKED_LAYER) != null) return
    val tileSet = TileSet("tileset", WAYMARKED_TILE_URL)
    style.addSource(RasterSource(WAYMARKED_SOURCE, tileSet, 256))
    val layer = RasterLayer(WAYMARKED_LAYER, WAYMARKED_SOURCE)
    layer.setProperties(PropertyFactory.rasterOpacity(0.85f))
    val belowId = style.getLayer(SEGMENTS_LAYER)?.id
        ?: style.getLayer(LOCKED_SEGMENTS_LAYER)?.id
        ?: style.getLayer(ROUTE_LAYER)?.id
        ?: style.getLayer(OVERLAY_LAYER)?.id
    if (belowId != null) {
        style.addLayerBelow(layer, belowId)
    } else {
        style.addLayer(layer)
    }
}

private fun updateSegmentsLayer(
    style: Style,
    segments: List<SegmentDto>,
    emphasize: Boolean,
    requiredIds: Set<Int> = emptySet(),
    excludedIds: Set<Int> = emptySet(),
    goldenIds: Set<Int> = emptySet(),
    disconnectedIds: Set<Int> = emptySet(),
) {
    val canonical = segments.filter { it.isCanonical() }
    val unlocked = canonical.filter { !it.isLocked() }
    val locked = canonical.filter { it.isLocked() }

    fun segmentColor(id: Int): String = when {
        disconnectedIds.contains(id) -> "#c53030"
        excludedIds.contains(id) -> "#c53030"
        requiredIds.contains(id) -> "#2b6cb0"
        goldenIds.contains(id) -> "#c99a2e"
        else -> if (emphasize) "#2e6b49" else "#9a9a90"
    }
    fun segmentWidth(id: Int): Float = when {
        requiredIds.contains(id) || goldenIds.contains(id) -> 6f
        else -> if (emphasize) 4f else 2f
    }
    fun segmentOpacity(id: Int): Float = when {
        excludedIds.contains(id) -> 0.85f
        else -> if (emphasize) 0.92f else 0.7f
    }

    val unlockedFeatures = unlocked.map { seg ->
        val properties = JsonObject().apply {
            addProperty("color", segmentColor(seg.id))
            addProperty("width", segmentWidth(seg.id))
            addProperty("opacity", segmentOpacity(seg.id))
            seg.id.let { addProperty("segmentId", it) }
        }
        Feature.fromGeometry(
            LineString.fromLngLats(seg.geometry.map { Point.fromLngLat(it.lng, it.lat) }),
            properties,
        )
    }
    val unlockedCollection = FeatureCollection.fromFeatures(unlockedFeatures)
    val existing = style.getSourceAs<GeoJsonSource>(SEGMENTS_SOURCE)
    if (existing != null) {
        existing.setGeoJson(unlockedCollection)
        (style.getLayer(SEGMENTS_LAYER) as? LineLayer)?.setProperties(
            PropertyFactory.lineColor(org.maplibre.android.style.expressions.Expression.get("color")),
            PropertyFactory.lineWidth(org.maplibre.android.style.expressions.Expression.get("width")),
            PropertyFactory.lineOpacity(org.maplibre.android.style.expressions.Expression.get("opacity")),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )
    } else {
        style.addSource(GeoJsonSource(SEGMENTS_SOURCE, unlockedCollection))
        val layer = LineLayer(SEGMENTS_LAYER, SEGMENTS_SOURCE)
        layer.setProperties(
            PropertyFactory.lineColor(org.maplibre.android.style.expressions.Expression.get("color")),
            PropertyFactory.lineWidth(org.maplibre.android.style.expressions.Expression.get("width")),
            PropertyFactory.lineOpacity(org.maplibre.android.style.expressions.Expression.get("opacity")),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )
        style.addLayer(layer)
    }

    val lockedCollection = FeatureCollection.fromFeatures(
        locked.map { seg ->
            Feature.fromGeometry(LineString.fromLngLats(seg.geometry.map { Point.fromLngLat(it.lng, it.lat) }))
        },
    )
    val lockedExisting = style.getSourceAs<GeoJsonSource>(LOCKED_SEGMENTS_SOURCE)
    if (lockedExisting != null) {
        lockedExisting.setGeoJson(lockedCollection)
        return
    }
    style.addSource(GeoJsonSource(LOCKED_SEGMENTS_SOURCE, lockedCollection))
    val lockedLayer = LineLayer(LOCKED_SEGMENTS_LAYER, LOCKED_SEGMENTS_SOURCE)
    val lockedWidth = if (emphasize) 4f else 2f
    val lockedOpacity = if (emphasize) 0.92f else 0.7f
    lockedLayer.setProperties(
        PropertyFactory.lineColor(if (emphasize) "#6b7280" else "#9a9a90"),
        PropertyFactory.lineWidth(lockedWidth),
        PropertyFactory.lineOpacity(lockedOpacity * 0.85f),
        PropertyFactory.lineDasharray(arrayOf(2f, 2f)),
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
    )
    style.addLayer(lockedLayer)
}

private fun updateGoldenSegmentsLayer(
    style: Style,
    segments: List<SegmentDto>,
    goldenIds: Set<Int>,
    hitIds: Set<Int> = emptySet(),
) {
    fun featuresFor(ids: Set<Int>) = segments
        .filter { it.isCanonical() && ids.contains(it.id) }
        .map { seg ->
            Feature.fromGeometry(LineString.fromLngLats(seg.geometry.map { Point.fromLngLat(it.lng, it.lat) }))
        }

    fun upsert(sourceId: String, layerId: String, ids: Set<Int>, width: Float, opacity: Float) {
        val collection = FeatureCollection.fromFeatures(featuresFor(ids))
        val existing = style.getSourceAs<GeoJsonSource>(sourceId)
        if (existing != null) {
            existing.setGeoJson(collection)
            return
        }
        if (ids.isEmpty()) return
        style.addSource(GeoJsonSource(sourceId, collection))
        val layer = LineLayer(layerId, sourceId)
        layer.setProperties(
            PropertyFactory.lineColor("#c99a2e"),
            PropertyFactory.lineWidth(width),
            PropertyFactory.lineOpacity(opacity),
            PropertyFactory.lineDasharray(arrayOf(4f, 2f)),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )
        val aboveRoute = style.getLayer(ROUTE_LAYER)?.id
        if (aboveRoute != null) {
            style.addLayerBelow(layer, aboveRoute)
        } else {
            style.addLayer(layer)
        }
    }

    upsert(GOLDEN_SEGMENTS_SOURCE, GOLDEN_SEGMENTS_LAYER, goldenIds, 5f, 0.85f)
    upsert(GOLDEN_HIT_SOURCE, GOLDEN_HIT_LAYER, hitIds, 8f, 0.98f)
}

private fun updateRouteLayer(style: Style, routeGeometry: List<GeoPoint>, routeColor: String) {
    val collection = if (routeGeometry.size >= 2) {
        FeatureCollection.fromFeature(
            Feature.fromGeometry(LineString.fromLngLats(routeGeometry.map { Point.fromLngLat(it.lng, it.lat) })),
        )
    } else {
        FeatureCollection.fromFeatures(emptyList())
    }
    val existing = style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE)
    if (existing != null) {
        existing.setGeoJson(collection)
        return
    }
    style.addSource(GeoJsonSource(ROUTE_SOURCE, collection))
    val layer = LineLayer(ROUTE_LAYER, ROUTE_SOURCE)
    layer.setProperties(
        PropertyFactory.lineColor(routeColor),
        PropertyFactory.lineWidth(5f),
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
    )
    style.addLayer(layer)
}

private fun updateTrackedLayer(style: Style, trackedGeometry: List<GeoPoint>) {
    val collection = if (trackedGeometry.size >= 2) {
        FeatureCollection.fromFeature(
            Feature.fromGeometry(LineString.fromLngLats(trackedGeometry.map { Point.fromLngLat(it.lng, it.lat) })),
        )
    } else {
        FeatureCollection.fromFeatures(emptyList())
    }
    val existing = style.getSourceAs<GeoJsonSource>(TRACKED_SOURCE)
    if (existing != null) {
        existing.setGeoJson(collection)
        return
    }
    if (trackedGeometry.size < 2) return
    style.addSource(GeoJsonSource(TRACKED_SOURCE, collection))
    val layer = LineLayer(TRACKED_LAYER, TRACKED_SOURCE)
    layer.setProperties(
        PropertyFactory.lineColor("#2563eb"),
        PropertyFactory.lineWidth(6f),
        PropertyFactory.lineOpacity(0.95f),
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
    )
    style.addLayer(layer)
}

private fun updateSelectedSegmentLayer(style: Style, segments: List<SegmentDto>, selectedSegmentId: Int?) {
    val segment = selectedSegmentId?.let { id -> segments.find { it.id == id } }
    val collection = if (segment != null && segment.geometry.size >= 2) {
        FeatureCollection.fromFeature(
            Feature.fromGeometry(LineString.fromLngLats(segment.geometry.map { Point.fromLngLat(it.lng, it.lat) })),
        )
    } else {
        FeatureCollection.fromFeatures(emptyList())
    }
    val existing = style.getSourceAs<GeoJsonSource>(HIGHLIGHT_SOURCE)
    if (existing != null) {
        existing.setGeoJson(collection)
        return
    }
    style.addSource(GeoJsonSource(HIGHLIGHT_SOURCE, collection))
    val layer = LineLayer(HIGHLIGHT_LAYER, HIGHLIGHT_SOURCE)
    layer.setProperties(
        PropertyFactory.lineColor("#2563eb"),
        PropertyFactory.lineWidth(6f),
        PropertyFactory.lineOpacity(0.95f),
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
    )
    style.addLayer(layer)
}

private fun updateEditVerticesLayer(style: Style, vertices: List<GeoPoint>?, selectedIndex: Int?) {
    val points = vertices.orEmpty()
    val features = points.mapIndexed { index, v ->
        val isEndpoint = index == 0 || index == points.lastIndex
        val isSelected = index == selectedIndex
        val color = when {
            isSelected -> "#2563eb"
            isEndpoint -> "#a5711c"
            else -> "#9a3b29"
        }
        val radius = when {
            isSelected -> 7f
            isEndpoint -> 5f
            else -> 4f
        }
        val properties = JsonObject().apply {
            addProperty("color", color)
            addProperty("radius", radius)
        }
        Feature.fromGeometry(Point.fromLngLat(v.lng, v.lat), properties)
    }
    val collection = FeatureCollection.fromFeatures(features)
    val existing = style.getSourceAs<GeoJsonSource>(EDIT_VERTICES_SOURCE)
    if (existing != null) {
        existing.setGeoJson(collection)
        return
    }
    if (features.isEmpty()) return
    style.addSource(GeoJsonSource(EDIT_VERTICES_SOURCE, collection))
    val layer = CircleLayer(EDIT_VERTICES_LAYER, EDIT_VERTICES_SOURCE)
    layer.setProperties(
        PropertyFactory.circleColor(org.maplibre.android.style.expressions.Expression.get("color")),
        PropertyFactory.circleRadius(org.maplibre.android.style.expressions.Expression.get("radius")),
        PropertyFactory.circleStrokeColor("#ffffff"),
        PropertyFactory.circleStrokeWidth(1.5f),
    )
    style.addLayer(layer)
}

private fun updateOverlayLayer(style: Style, overlayLine: List<GeoPoint>) {
    val collection = if (overlayLine.size >= 2) {
        FeatureCollection.fromFeature(
            Feature.fromGeometry(LineString.fromLngLats(overlayLine.map { Point.fromLngLat(it.lng, it.lat) })),
        )
    } else {
        FeatureCollection.fromFeatures(emptyList())
    }
    val existing = style.getSourceAs<GeoJsonSource>(OVERLAY_SOURCE)
    if (existing != null) {
        existing.setGeoJson(collection)
        return
    }
    style.addSource(GeoJsonSource(OVERLAY_SOURCE, collection))
    val layer = LineLayer(OVERLAY_LAYER, OVERLAY_SOURCE)
    layer.setProperties(
        PropertyFactory.lineColor("#9a3b29"),
        PropertyFactory.lineWidth(5f),
        PropertyFactory.lineOpacity(0.9f),
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
    )
    style.addLayer(layer)
}

private fun updateNodesLayer(
    style: Style,
    nodes: List<NodeDto>,
    selectedNodeId: Int?,
    moveNodeId: Int?,
    homeNodeId: Int?,
    startNodeId: Int? = null,
    endNodeId: Int? = null,
    mustVisitNodeIds: List<Int> = emptyList(),
    isLoop: Boolean = true,
) {
    val features = nodes.map { node ->
        val color = when {
            node.id == moveNodeId -> "#1e4a32"
            node.id == selectedNodeId -> "#2563eb"
            node.id == startNodeId -> "#a5711c"
            !isLoop && node.id == endNodeId -> "#a5711c"
            mustVisitNodeIds.contains(node.id) -> "#2b6cb0"
            node.id == homeNodeId || (homeNodeId == null && node.isHome) -> "#a5711c"
            else -> "#2e6b49"
        }
        val radius = when {
            node.id == moveNodeId || node.id == selectedNodeId -> 8f
            node.id == startNodeId || (!isLoop && node.id == endNodeId) -> 7f
            mustVisitNodeIds.contains(node.id) -> 6f
            node.id == homeNodeId || (homeNodeId == null && node.isHome) -> 5f
            else -> 3f
        }
        val properties = JsonObject().apply {
            addProperty("color", color)
            addProperty("radius", radius)
        }
        Feature.fromGeometry(Point.fromLngLat(node.lng, node.lat), properties)
    }
    val collection = FeatureCollection.fromFeatures(features)
    val existing = style.getSourceAs<GeoJsonSource>(NODES_SOURCE)
    if (existing != null) {
        existing.setGeoJson(collection)
        return
    }
    style.addSource(GeoJsonSource(NODES_SOURCE, collection))
    val layer = CircleLayer(NODES_LAYER, NODES_SOURCE)
    layer.setProperties(
        PropertyFactory.circleColor(org.maplibre.android.style.expressions.Expression.get("color")),
        PropertyFactory.circleRadius(org.maplibre.android.style.expressions.Expression.get("radius")),
        PropertyFactory.circleStrokeColor("#ffffff"),
        PropertyFactory.circleStrokeWidth(1f),
    )
    style.addLayer(layer)
}

private fun updateNodeBadgesLayer(style: Style, nodes: List<NodeDto>, badges: Map<Int, String>) {
    val features = nodes.mapNotNull { node ->
        val badge = badges[node.id] ?: return@mapNotNull null
        val properties = JsonObject().apply { addProperty("badge", badge) }
        Feature.fromGeometry(Point.fromLngLat(node.lng, node.lat), properties)
    }
    val collection = FeatureCollection.fromFeatures(features)
    val existing = style.getSourceAs<GeoJsonSource>(NODE_BADGES_SOURCE)
    if (existing != null) {
        existing.setGeoJson(collection)
        return
    }
    if (features.isEmpty()) return
    style.addSource(GeoJsonSource(NODE_BADGES_SOURCE, collection))
    val layer = org.maplibre.android.style.layers.SymbolLayer(NODE_BADGES_LAYER, NODE_BADGES_SOURCE)
    layer.setProperties(
        PropertyFactory.textField(org.maplibre.android.style.expressions.Expression.get("badge")),
        PropertyFactory.textSize(11f),
        PropertyFactory.textColor("#ffffff"),
        PropertyFactory.textHaloColor("#1a1a1a"),
        PropertyFactory.textHaloWidth(1.5f),
        PropertyFactory.textAllowOverlap(true),
        PropertyFactory.textIgnorePlacement(true),
        PropertyFactory.textOffset(arrayOf(0f, -1.2f)),
    )
    style.addLayer(layer)
}

private fun updateSegmentBadgesLayer(style: Style, segments: List<SegmentDto>, badges: Map<Int, String>) {
    val features = segments.mapNotNull { segment ->
        val badge = badges[segment.id] ?: return@mapNotNull null
        if (segment.geometry.isEmpty()) return@mapNotNull null
        val mid = segment.geometry[segment.geometry.size / 2]
        val properties = JsonObject().apply { addProperty("badge", badge) }
        Feature.fromGeometry(Point.fromLngLat(mid.lng, mid.lat), properties)
    }
    val collection = FeatureCollection.fromFeatures(features)
    val existing = style.getSourceAs<GeoJsonSource>(SEGMENT_BADGES_SOURCE)
    if (existing != null) {
        existing.setGeoJson(collection)
        return
    }
    if (features.isEmpty()) return
    style.addSource(GeoJsonSource(SEGMENT_BADGES_SOURCE, collection))
    val layer = org.maplibre.android.style.layers.SymbolLayer(SEGMENT_BADGES_LAYER, SEGMENT_BADGES_SOURCE)
    layer.setProperties(
        PropertyFactory.textField(org.maplibre.android.style.expressions.Expression.get("badge")),
        PropertyFactory.textSize(10f),
        PropertyFactory.textColor("#ffffff"),
        PropertyFactory.textHaloColor("#1a1a1a"),
        PropertyFactory.textHaloWidth(1.5f),
        PropertyFactory.textAllowOverlap(true),
        PropertyFactory.textIgnorePlacement(true),
    )
    style.addLayer(layer)
}

private fun updateStationsLayer(style: Style, stations: List<RouteStation>, completedWaypointIndex: Int) {
    val features = stations.mapIndexed { idx, s ->
        val (color, radius) = when {
            idx <= completedWaypointIndex -> "#6b7280" to 5f
            idx == completedWaypointIndex + 1 -> "#2563eb" to 9f
            idx == 0 -> "#a5711c" to 7f
            else -> "#2e6b49" to 7f
        }
        val properties = JsonObject().apply {
            addProperty("color", color)
            addProperty("radius", radius)
        }
        Feature.fromGeometry(Point.fromLngLat(s.lng, s.lat), properties)
    }
    val collection = FeatureCollection.fromFeatures(features)
    val existing = style.getSourceAs<GeoJsonSource>(STATIONS_SOURCE)
    if (existing != null) {
        existing.setGeoJson(collection)
        return
    }
    style.addSource(GeoJsonSource(STATIONS_SOURCE, collection))
    val layer = CircleLayer(STATIONS_LAYER, STATIONS_SOURCE)
    layer.setProperties(
        PropertyFactory.circleColor(org.maplibre.android.style.expressions.Expression.get("color")),
        PropertyFactory.circleRadius(org.maplibre.android.style.expressions.Expression.get("radius")),
        PropertyFactory.circleStrokeColor("#ffffff"),
        PropertyFactory.circleStrokeWidth(2f),
    )
    style.addLayer(layer)
}

private fun updateMyLocationLayer(style: Style, myLocation: GeoPoint?) {
    val collection = if (myLocation != null) {
        FeatureCollection.fromFeature(Feature.fromGeometry(Point.fromLngLat(myLocation.lng, myLocation.lat)))
    } else {
        FeatureCollection.fromFeatures(emptyList())
    }
    val existing = style.getSourceAs<GeoJsonSource>(ME_SOURCE)
    if (existing != null) {
        existing.setGeoJson(collection)
        return
    }
    style.addSource(GeoJsonSource(ME_SOURCE, collection))
    val layer = CircleLayer(ME_LAYER, ME_SOURCE)
    layer.setProperties(
        PropertyFactory.circleColor("#2b6cb0"),
        PropertyFactory.circleRadius(8f),
        PropertyFactory.circleStrokeColor("#ffffff"),
        PropertyFactory.circleStrokeWidth(2f),
    )
    style.addLayer(layer)
}
