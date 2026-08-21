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
import androidx.compose.foundation.clickable
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
import com.routy.app.logic.route.VoiceCue
import com.routy.app.logic.route.VoiceCueTracker
import com.routy.app.logic.route.WaypointProgressTracker
import com.routy.app.map.BaseMapStyle
import com.routy.app.map.MapStyleSwitcher
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
                RouteViewModel(app.apiClientProvider, app.routeProgressStore, app.networkCache, app.bootstrapLoader, app.mapTilePrefetchScheduler)
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

    DisposableEffect(uiState.keepScreenOn, uiState.tracking) {
        if (uiState.keepScreenOn && uiState.tracking) {
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
            accountLocaleTag = accountLocaleTag,
            onDismiss = viewModel::dismissCompletionStats,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoutePresetButtons(uiState: RouteUiState, viewModel: RouteViewModel) {
    val loading = uiState.status == RouteStatus.LOADING
    val hasStart = uiState.startNodeId != null
    val canGenerate = !loading && hasStart && !uiState.offlineCached
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CompactButton(onClick = { viewModel.suggest("short") }, enabled = canGenerate) {
            Text(stringResource(if (loading) R.string.route_generating else R.string.route_preset_short))
        }
        CompactOutlinedButton(onClick = { viewModel.suggest("long") }, enabled = canGenerate) {
            Text(stringResource(R.string.route_preset_long))
        }
        CompactOutlinedButton(onClick = { viewModel.surprise() }, enabled = canGenerate) {
            Text(stringResource(R.string.route_preset_surprise))
        }
    }
}

private enum class DockLevel { COLLAPSED, DEFAULT, EXPANDED }

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
            goldenSegmentIds = goldenSegmentIds,
            goldenHitIds = goldenHitIds,
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
        }
    }
}

@Composable
private fun RouteBottomDock(
    level: DockLevel,
    onCycleCollapse: () -> Unit,
    onToggleExpanded: () -> Unit,
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
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onCycleCollapse),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = summary,
                )
                if (level != DockLevel.COLLAPSED) {
                    Text(
                        stringResource(
                            if (level == DockLevel.EXPANDED) R.string.route_less_options else R.string.route_more_options,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable(onClick = onToggleExpanded)
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                    )
                }
                IconButton(onClick = onCycleCollapse, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = if (level == DockLevel.COLLAPSED) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = stringResource(
                            if (level == DockLevel.COLLAPSED) R.string.route_panel_expand else R.string.route_panel_collapse,
                        ),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            AnimatedVisibility(visible = level != DockLevel.COLLAPSED) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    primary()
                }
            }
            AnimatedVisibility(visible = level == DockLevel.EXPANDED) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
    var dockLevel by remember { mutableStateOf(DockLevel.DEFAULT) }

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
            fitKey = uiState.nodes.size,
            fitToRouteOnly = false,
            emphasizeNetwork = true,
            modifier = Modifier.weight(1f),
        )

        RouteBottomDock(
            level = dockLevel,
            onCycleCollapse = {
                dockLevel = if (dockLevel == DockLevel.COLLAPSED) DockLevel.DEFAULT else DockLevel.COLLAPSED
            },
            onToggleExpanded = {
                dockLevel = if (dockLevel == DockLevel.EXPANDED) DockLevel.DEFAULT else DockLevel.EXPANDED
            },
            summary = {
                Text(
                    stringResource(R.string.route_point_balance, uiState.totalPoints, uiState.streakMultiplier),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            },
            primary = {
                NodeDropdown(
                    stringResource(R.string.route_start),
                    uiState.nodes,
                    uiState.startNodeId,
                    { id -> id?.let(viewModel::setStartNodeId) },
                    dense = true,
                )
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
                uiState.messageRes?.let {
                    Text(stringResource(it), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                }
            },
            expandedContent = {
                if (uiState.favorites.isNotEmpty()) {
                    FavoritesPicker(uiState.favorites, uiState.status == RouteStatus.LOADING, viewModel)
                }
                if (!uiState.isLoop) {
                    NodeDropdown(
                        stringResource(R.string.route_destination),
                        uiState.nodes,
                        uiState.destinationNodeId,
                        { id -> id?.let(viewModel::setDestinationNodeId) },
                        dense = true,
                    )
                }
                NodeDropdown(
                    "${stringResource(R.string.route_waypoint)} (${stringResource(R.string.common_optional)})",
                    uiState.nodes,
                    uiState.waypointNodeId,
                    viewModel::setWaypointNodeId,
                    stringResource(R.string.route_waypoint_none),
                    dense = true,
                )
            },
        )
    }
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
    var dockLevel by remember { mutableStateOf(DockLevel.DEFAULT) }
    var hasLocationPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasLocationPermission = granted
        if (granted) viewModel.setWatchingLocation(true)
    }
    fun needsNotificationPermission() =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    var pendingStartTracking by remember { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (pendingStartTracking) {
            pendingStartTracking = false
            viewModel.setTracking(true)
        }
    }
    val trackingLocationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasLocationPermission = granted
        if (!granted) {
            pendingStartTracking = false
            return@rememberLauncherForActivityResult
        }
        if (needsNotificationPermission()) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            pendingStartTracking = false
            viewModel.setTracking(true)
        }
    }
    fun requestStartTracking() {
        pendingStartTracking = true
        when {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ->
                trackingLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            needsNotificationPermission() -> notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> {
                pendingStartTracking = false
                viewModel.setTracking(true)
            }
        }
    }
    var pendingDiscard by remember { mutableStateOf(false) }

    if (pendingDiscard) {
        AlertDialog(
            onDismissRequest = { pendingDiscard = false },
            confirmButton = {
                TextButton(onClick = {
                    pendingDiscard = false
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

    // While tracking, the foreground service owns GPS / TTS / progress so it survives the pocket.
    ActiveRouteLocationEffect(uiState, hasLocationPermission && !uiState.tracking, viewModel)
    if (uiState.mode == RouteMode.ACTIVE) {
        ActiveRouteTrackingServiceEffect(uiState, route, viewModel, accountLocaleTag)
        if (!uiState.tracking) {
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

    Column(modifier = modifier.fillMaxSize()) {
        RouteMapChrome(
            uiState = uiState,
            mapStyle = mapStyle,
            onMapStyle = { mapStyle = it },
            waymarkedOverlay = waymarkedOverlay,
            onWaymarkedOverlay = { waymarkedOverlay = it },
            routeGeometry = route.geometry,
            stations = route.stations,
            goldenSegmentIds = uiState.todayGoldenSegmentIds,
            goldenHitIds = uiState.goldenHitIds,
            fitKey = uiState.token.ifEmpty { route.nodeChain.joinToString("-") },
            fitToRouteOnly = true,
            emphasizeNetwork = false,
            completedWaypointIndex = uiState.completedWaypointIndex,
            modifier = Modifier.weight(1f),
        )

        RouteBottomDock(
            level = dockLevel,
            onCycleCollapse = {
                dockLevel = if (dockLevel == DockLevel.COLLAPSED) DockLevel.DEFAULT else DockLevel.COLLAPSED
            },
            onToggleExpanded = {
                dockLevel = if (dockLevel == DockLevel.EXPANDED) DockLevel.DEFAULT else DockLevel.EXPANDED
            },
            summary = {
                CompactMeta("${"%.1f".format(route.lengthM / 1000.0)} km")
                CompactMeta("${route.durationMin} min")
                route.elevation?.let { CompactMeta("↗${it.gainM}m") }
                if (uiState.mode == RouteMode.ACTIVE && uiState.tracking) {
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
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
                            CompactCheck(
                                checked = uiState.watchingLocation,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        if (hasLocationPermission) viewModel.setWatchingLocation(true)
                                        else permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                    } else {
                                        viewModel.setWatchingLocation(false)
                                    }
                                },
                                label = stringResource(R.string.route_location_check),
                            )
                            CompactCheck(
                                checked = uiState.voiceEnabled,
                                onCheckedChange = viewModel::setVoiceEnabled,
                                label = stringResource(R.string.route_voice_check),
                                enabled = uiState.watchingLocation,
                            )
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (uiState.watchingLocation) {
                                CompactOutlinedButton(onClick = {
                                    if (uiState.tracking) viewModel.setTracking(false)
                                    else requestStartTracking()
                                }) {
                                    Text(stringResource(if (uiState.tracking) R.string.route_tracking_on else R.string.route_track))
                                }
                            }
                            val canComplete = !uiState.tracking || uiState.completedWaypointIndex >= route.stations.lastIndex
                            CompactButton(onClick = viewModel::complete, enabled = canComplete) {
                                Text(stringResource(R.string.route_complete_button))
                            }
                            CompactOutlinedButton(onClick = { pendingDiscard = true }) {
                                Text(stringResource(R.string.route_discard_button))
                            }
                        }
                        if (uiState.tracking) {
                            BatteryOptimizationPrompt(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }

                uiState.messageRes?.let {
                    Text(stringResource(it), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                }
            },
            expandedContent = {
                when {
                    uiState.mode == RouteMode.SUGGESTING && uiState.pendingShareToken == null -> {
                        RoutePresetButtons(uiState, viewModel)
                        DenseTextField(
                            value = uiState.suggestFavoriteName,
                            onValueChange = viewModel::setSuggestFavoriteName,
                            label = stringResource(R.string.route_favorite_name_placeholder),
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            CompactOutlinedButton(
                                onClick = { viewModel.saveFavoriteFromSuggestion(uiState.suggestFavoriteName) },
                                enabled = !uiState.savingFavorite && uiState.suggestFavoriteName.isNotBlank(),
                            ) {
                                Text(stringResource(R.string.route_save_favorite))
                            }
                            CompactOutlinedButton(onClick = onStartRecording, enabled = !loading) {
                                Text(stringResource(R.string.record_entry_point))
                            }
                        }
                        uiState.pointPreview?.let { PointPreviewLines(it) }
                    }
                    uiState.mode == RouteMode.ACTIVE -> {
                        DenseTextField(
                            value = uiState.nickname,
                            onValueChange = viewModel::setNickname,
                            label = stringResource(R.string.route_name_label),
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            CompactOutlinedButton(onClick = viewModel::saveNickname, enabled = !uiState.nicknameSaving) {
                                Text(stringResource(R.string.route_save_name))
                            }
                            CompactOutlinedButton(
                                onClick = viewModel::saveFavorite,
                                enabled = !uiState.savingFavorite && uiState.nickname.isNotBlank(),
                            ) {
                                Text(stringResource(R.string.route_save_favorite))
                            }
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
    DisposableEffect(uiState.watchingLocation, hasLocationPermission, uiState.tracking) {
        if (!uiState.watchingLocation || !hasLocationPermission || uiState.tracking) {
            return@DisposableEffect onDispose {}
        }
        val client = LocationServices.getFusedLocationProviderClient(context)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L).build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { viewModel.setMyLocation(GeoPoint(it.latitude, it.longitude)) }
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

    LaunchedEffect(uiState.tracking) {
        if (!uiState.tracking) {
            RouteTrackingForegroundService.stop(context)
            service = null
        }
    }

    DisposableEffect(uiState.tracking, routeKey, accountLocaleTag) {
        if (!uiState.tracking) {
            return@DisposableEffect onDispose {}
        }

        RouteTrackingForegroundService.start(
            context = context,
            stations = route.stations,
            routeKey = routeKey,
            accountLocaleTag = accountLocaleTag,
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

    LaunchedEffect(uiState.tracking, uiState.voiceEnabled) {
        if (uiState.tracking) {
            RouteTrackingForegroundService.setVoiceEnabled(context, uiState.voiceEnabled)
        }
    }

    LaunchedEffect(service) {
        val bound = service ?: return@LaunchedEffect
        bound.state.collect { serviceState ->
            serviceState.myLocation?.let(viewModel::setMyLocation)
            viewModel.syncFromTrackingService(
                completedWaypointIndex = serviceState.completedWaypointIndex,
                voiceAnnouncedIndex = serviceState.voiceAnnouncedIndex,
                trackingActive = serviceState.active,
            )
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

    LaunchedEffect(uiState.completedWaypointIndex, route.nodeChain) {
        progressTracker.restore(uiState.completedWaypointIndex)
    }
    LaunchedEffect(uiState.voiceAnnouncedIndex, route.nodeChain) {
        voiceTracker.restore(uiState.voiceAnnouncedIndex)
    }

    val location = uiState.myLocation
    val voiceActive = uiState.voiceEnabled && uiState.watchingLocation
    LaunchedEffect(location, voiceActive, uiState.tracking) {
        if (location == null) return@LaunchedEffect
        val latLng = LatLng(location.lat, location.lng)
        if (voiceActive) voiceTracker.onLocationUpdate(latLng)?.let { pendingCue = it }
        if (uiState.tracking) {
            progressTracker.onLocationUpdate(latLng)?.let { completed ->
                if (completed > uiState.completedWaypointIndex) {
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
private fun FavoritesPicker(favorites: List<FavoriteEntry>, loading: Boolean, viewModel: RouteViewModel) {
    var selected by remember(favorites) { mutableStateOf(favorites.firstOrNull()) }
    var menuExpanded by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<FavoriteEntry?>(null) }

    ExposedDropdownMenuBox(expanded = menuExpanded, onExpandedChange = { menuExpanded = it }) {
        OutlinedTextField(
            value = selected?.let { "${it.name} · ${"%.1f".format(it.display.lengthM / 1000.0)} km" }
                ?: stringResource(R.string.route_favorites_pick),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            textStyle = MaterialTheme.typography.labelSmall,
            label = { Text(stringResource(R.string.route_favorites_title), style = MaterialTheme.typography.labelSmall) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuExpanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .heightIn(max = 52.dp),
        )
        ExposedDropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            favorites.forEach { fav ->
                DropdownMenuItem(
                    text = {
                        Text(
                            "${fav.name} · ${"%.1f".format(fav.display.lengthM / 1000.0)} km",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    onClick = {
                        selected = fav
                        menuExpanded = false
                    },
                )
            }
        }
    }

    selected?.let { fav ->
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CompactButton(onClick = { viewModel.takeFavorite(fav) }, enabled = !loading) {
                Text(stringResource(R.string.route_favorite_take))
            }
            CompactOutlinedButton(onClick = { viewModel.toggleShare(fav) }) {
                Text(stringResource(if (fav.shareToken != null) R.string.route_favorite_unshare else R.string.route_favorite_share))
            }
            if (fav.shareToken != null) {
                CompactOutlinedButton(onClick = { viewModel.copyFavoriteShareLink(fav) }) {
                    Text(stringResource(R.string.route_favorite_copy_link))
                }
            }
            CompactOutlinedButton(onClick = { pendingDelete = fav }) {
                Text(stringResource(R.string.route_favorite_delete))
            }
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NodeDropdown(
    label: String,
    nodes: List<NodeDto>,
    selectedId: Int?,
    onSelect: (Int?) -> Unit,
    noneLabel: String? = null,
    dense: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    val collator = remember { Collator.getInstance() }
    val sorted = remember(nodes) { nodes.sortedWith(compareBy(collator) { it.name ?: "#${it.id}" }) }
    val selectedLabel = sorted.firstOrNull { it.id == selectedId }?.let { it.name ?: "#${it.id}" } ?: (noneLabel ?: "")
    val textStyle = if (dense) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label, style = if (dense) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall) },
            textStyle = textStyle,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .then(if (dense) Modifier.heightIn(max = 52.dp) else Modifier),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (noneLabel != null) {
                DropdownMenuItem(
                    text = { Text(noneLabel, style = textStyle) },
                    onClick = {
                        onSelect(null)
                        expanded = false
                    },
                )
            }
            sorted.forEach { node ->
                DropdownMenuItem(
                    text = { Text(node.name ?: "#${node.id}", style = textStyle) },
                    onClick = {
                        onSelect(node.id)
                        expanded = false
                    },
                )
            }
        }
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
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
            }
        },
    )
}

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
