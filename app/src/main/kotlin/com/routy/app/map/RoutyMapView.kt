package com.routy.app.map

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.gson.JsonObject
import com.routy.app.logic.api.GeoPoint
import com.routy.app.logic.api.NodeDto
import com.routy.app.logic.api.RouteStation
import com.routy.app.logic.api.SegmentDto
import com.routy.app.logic.api.isCanonical
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
import org.maplibre.android.style.sources.GeoJsonSource
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

private const val SEGMENTS_SOURCE = "routy-segments"
private const val SEGMENTS_LAYER = "routy-segments-layer"
private const val ROUTE_SOURCE = "routy-route"
private const val ROUTE_LAYER = "routy-route-layer"
private const val NODES_SOURCE = "routy-nodes"
private const val NODES_LAYER = "routy-nodes-layer"
private const val STATIONS_SOURCE = "routy-stations"
private const val STATIONS_LAYER = "routy-stations-layer"
private const val ME_SOURCE = "routy-me"
private const val ME_LAYER = "routy-me-layer"

/**
 * Read-only network + route display: every known segment drawn faint in the background, the
 * active/suggested route highlighted on top of it, network nodes as small dots, the current
 * route's stations as bigger accent-colored dots, and an optional live position marker. Network
 * *editing* (the click-to-edit popups) stays on the WebView Map tab — this native map exists for
 * the one thing that needs to survive the phone going in a pocket: following an active route.
 */
@Composable
fun RoutyMapView(
    style: BaseMapStyle,
    nodes: List<NodeDto>,
    segments: List<SegmentDto>,
    routeGeometry: List<GeoPoint>,
    stations: List<RouteStation>,
    myLocation: GeoPoint?,
    fitKey: Any?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember { MapView(context) }
    var maplibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var loadedStyle by remember { mutableStateOf<Style?>(null) }

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

    LaunchedEffect(mapView, style) {
        loadedStyle = null
        mapView.getMapAsync { map ->
            maplibreMap = map
            map.setStyle(Style.Builder().fromUri(style.assetUri)) { newStyle -> loadedStyle = newStyle }
        }
    }

    LaunchedEffect(loadedStyle, nodes, segments, routeGeometry, stations, myLocation) {
        val currentStyle = loadedStyle ?: return@LaunchedEffect
        updateSegmentsLayer(currentStyle, segments)
        updateRouteLayer(currentStyle, routeGeometry)
        updateNodesLayer(currentStyle, nodes)
        updateStationsLayer(currentStyle, stations)
        updateMyLocationLayer(currentStyle, myLocation)
    }

    LaunchedEffect(loadedStyle, fitKey) {
        val map = maplibreMap ?: return@LaunchedEffect
        if (loadedStyle == null) return@LaunchedEffect
        val points = buildList {
            nodes.forEach { add(LatLng(it.lat, it.lng)) }
            routeGeometry.forEach { add(LatLng(it.lat, it.lng)) }
        }
        when {
            points.isEmpty() -> {}
            points.size == 1 -> map.moveCamera(CameraUpdateFactory.newLatLngZoom(points[0], 15.0))
            else -> map.moveCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(points).build(), 64))
        }
    }
}

private fun updateSegmentsLayer(style: Style, segments: List<SegmentDto>) {
    val features = segments.filter { it.isCanonical() }.map { seg ->
        Feature.fromGeometry(LineString.fromLngLats(seg.geometry.map { Point.fromLngLat(it.lng, it.lat) }))
    }
    val collection = FeatureCollection.fromFeatures(features)
    val existing = style.getSourceAs<GeoJsonSource>(SEGMENTS_SOURCE)
    if (existing != null) {
        existing.setGeoJson(collection)
        return
    }
    style.addSource(GeoJsonSource(SEGMENTS_SOURCE, collection))
    val layer = LineLayer(SEGMENTS_LAYER, SEGMENTS_SOURCE)
    layer.setProperties(
        PropertyFactory.lineColor("#9a9a90"),
        PropertyFactory.lineWidth(2f),
        PropertyFactory.lineOpacity(0.7f),
    )
    style.addLayer(layer)
}

private fun updateRouteLayer(style: Style, routeGeometry: List<GeoPoint>) {
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
        PropertyFactory.lineColor("#2e6b49"),
        PropertyFactory.lineWidth(5f),
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
    )
    style.addLayer(layer)
}

private fun updateNodesLayer(style: Style, nodes: List<NodeDto>) {
    val features = nodes.map { node -> Feature.fromGeometry(Point.fromLngLat(node.lng, node.lat)) }
    val collection = FeatureCollection.fromFeatures(features)
    val existing = style.getSourceAs<GeoJsonSource>(NODES_SOURCE)
    if (existing != null) {
        existing.setGeoJson(collection)
        return
    }
    style.addSource(GeoJsonSource(NODES_SOURCE, collection))
    val layer = CircleLayer(NODES_LAYER, NODES_SOURCE)
    layer.setProperties(
        PropertyFactory.circleColor("#2e6b49"),
        PropertyFactory.circleRadius(3f),
        PropertyFactory.circleStrokeColor("#ffffff"),
        PropertyFactory.circleStrokeWidth(1f),
    )
    style.addLayer(layer)
}

private fun updateStationsLayer(style: Style, stations: List<RouteStation>) {
    val features = stations.mapIndexed { idx, s ->
        val properties = JsonObject().apply { addProperty("color", if (idx == 0) "#a5711c" else "#2e6b49") }
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
        PropertyFactory.circleRadius(7f),
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
