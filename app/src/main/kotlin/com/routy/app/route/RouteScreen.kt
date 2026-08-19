package com.routy.app.route

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.pm.PackageManager
import android.os.Looper
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.routy.app.logic.geo.LatLng
import com.routy.app.logic.route.VoiceCue
import com.routy.app.logic.route.VoiceCueTracker
import com.routy.app.logic.route.WaypointProgressTracker
import com.routy.app.map.BaseMapStyle
import com.routy.app.map.MapStyleSwitcher
import com.routy.app.map.RoutyMapView
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
            onStartRecording = onStartRecording,
            modifier = modifier,
        )
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        var mapStyle by remember { mutableStateOf(BaseMapStyle.STREETS) }
        RoutyMapView(
            style = mapStyle,
            nodes = uiState.nodes,
            segments = uiState.segments,
            routeGeometry = route.geometry,
            stations = route.stations,
            myLocation = uiState.myLocation,
            fitKey = uiState.token.ifEmpty { route.nodeChain.joinToString("-") },
            completedWaypointIndex = uiState.completedWaypointIndex,
            modifier = Modifier.fillMaxSize(),
        )

        if (uiState.showControls) {
            Column(
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (uiState.offlineCached) OfflineBanner()
                MapStyleSwitcher(selected = mapStyle, onSelect = { mapStyle = it })
            }
        }

        IconButton(
            onClick = viewModel::toggleControls,
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
        ) {
            Icon(Icons.Filled.Menu, contentDescription = null)
        }

        if (uiState.showControls) {
            RouteOverlayPanel(
                uiState = uiState,
                route = route,
                viewModel = viewModel,
                accountLocaleTag = accountLocaleTag,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            )
        }
    }

    val pointsEarned = uiState.completionPointsEarned
    if (pointsEarned != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissCompletionStats,
            confirmButton = { TextButton(onClick = viewModel::dismissCompletionStats) { Text(stringResource(R.string.common_ok)) } },
            title = { Text(stringResource(R.string.route_completion_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(
                            R.string.route_completion_points,
                            pointsEarned,
                            uiState.completionStreakMultiplier ?: 1.0,
                        ),
                    )
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuggestingMapLayout(
    uiState: RouteUiState,
    viewModel: RouteViewModel,
    onStartRecording: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var mapStyle by remember { mutableStateOf(BaseMapStyle.STREETS) }
    var expanded by remember { mutableStateOf(false) }
    val hasSecondary = uiState.favorites.isNotEmpty() || !uiState.isLoop || uiState.waypointNodeId != null

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
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (uiState.offlineCached) OfflineBanner()
            MapStyleSwitcher(selected = mapStyle, onSelect = { mapStyle = it })
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AnimatedVisibility(visible = expanded) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .padding(8.dp)
                            .heightIn(max = 180.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (uiState.favorites.isNotEmpty()) {
                            FavoritesCard(uiState.favorites, uiState.status == RouteStatus.LOADING, viewModel)
                        }
                        if (!uiState.isLoop) {
                            NodeDropdown(stringResource(R.string.route_destination), uiState.nodes, uiState.destinationNodeId, viewModel::setDestinationNodeId, dense = true)
                        }
                        NodeDropdown(
                            "${stringResource(R.string.route_waypoint)} (${stringResource(R.string.common_optional)})",
                            uiState.nodes,
                            uiState.waypointNodeId,
                            viewModel::setWaypointNodeId,
                            stringResource(R.string.route_waypoint_none),
                            dense = true,
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 3.dp,
                shadowElevation = 4.dp,
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    NodeDropdown(stringResource(R.string.route_start), uiState.nodes, uiState.startNodeId, viewModel::setStartNodeId, dense = true)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = uiState.isLoop, onCheckedChange = viewModel::setIsLoop)
                            Text(stringResource(R.string.route_loop), style = MaterialTheme.typography.labelSmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = uiState.explorerMode, onCheckedChange = viewModel::setExplorerMode)
                            Text(stringResource(R.string.route_explorer_mode), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    val loading = uiState.status == RouteStatus.LOADING
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CompactButton(onClick = { viewModel.suggest() }, enabled = !loading && uiState.startNodeId != null) {
                            Text(stringResource(if (loading) R.string.route_generating else R.string.route_suggest), style = MaterialTheme.typography.labelMedium)
                        }
                        CompactOutlinedButton(onClick = { viewModel.suggest("short") }, enabled = !loading) {
                            Text(stringResource(R.string.route_preset_short), style = MaterialTheme.typography.labelMedium)
                        }
                        CompactOutlinedButton(onClick = { viewModel.suggest("long") }, enabled = !loading) {
                            Text(stringResource(R.string.route_preset_long), style = MaterialTheme.typography.labelMedium)
                        }
                        CompactOutlinedButton(onClick = { viewModel.discover() }, enabled = !loading && uiState.startNodeId != null) {
                            Text(stringResource(R.string.route_preset_discover), style = MaterialTheme.typography.labelMedium)
                        }
                        CompactOutlinedButton(onClick = { viewModel.surprise() }, enabled = !loading && uiState.startNodeId != null) {
                            Text(stringResource(R.string.route_preset_surprise), style = MaterialTheme.typography.labelMedium)
                        }
                        CompactOutlinedButton(onClick = onStartRecording) {
                            Text(stringResource(R.string.record_entry_point), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    if (hasSecondary || uiState.favorites.isNotEmpty()) {
                        TextButton(onClick = { expanded = !expanded }) {
                            Text(
                                stringResource(if (expanded) R.string.route_less_options else R.string.route_more_options),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    uiState.messageRes?.let {
                        Text(stringResource(it), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RouteOverlayPanel(
    uiState: RouteUiState,
    route: com.routy.app.logic.api.RouteDisplayPayload,
    viewModel: RouteViewModel,
    accountLocaleTag: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var hasLocationPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasLocationPermission = granted
        if (granted) viewModel.setWatchingLocation(true)
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

    ActiveRouteLocationEffect(uiState, hasLocationPermission, viewModel)

    if (uiState.mode == RouteMode.ACTIVE) {
        ActiveTrackingEffects(uiState, route, viewModel, accountLocaleTag)
    }

    Card(modifier = modifier.fillMaxWidth().padding(8.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .heightIn(max = 320.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(onClick = {}, label = { Text("${"%.2f".format(route.lengthM / 1000.0)} km", style = MaterialTheme.typography.labelSmall) })
                AssistChip(onClick = {}, label = { Text("${route.durationMin} min", style = MaterialTheme.typography.labelSmall) })
                route.elevation?.let { AssistChip(onClick = {}, label = { Text("↗${it.gainM}m", style = MaterialTheme.typography.labelSmall) }) }
                if (uiState.mode == RouteMode.ACTIVE && uiState.tracking) {
                    TrackProgressChip(uiState, route)
                }
            }

            uiState.pendingShareToken?.let { token ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        uiState.sharedRouteName ?: stringResource(R.string.route_share_preview),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f),
                    )
                    CompactButton(onClick = { viewModel.acceptSharedRoute(token) }) {
                        Text(stringResource(R.string.route_accept), style = MaterialTheme.typography.labelMedium)
                    }
                    CompactOutlinedButton(onClick = viewModel::dismissSharedRoute) {
                        Text(stringResource(R.string.route_share_dismiss), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            if (uiState.mode == RouteMode.SUGGESTING && uiState.pendingShareToken == null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CompactOutlinedButton(onClick = { viewModel.adjust("shorter") }) { Text(stringResource(R.string.route_shorter), style = MaterialTheme.typography.labelMedium) }
                    CompactOutlinedButton(onClick = { viewModel.adjust("longer") }) { Text(stringResource(R.string.route_longer), style = MaterialTheme.typography.labelMedium) }
                    CompactOutlinedButton(onClick = viewModel::another) { Text(stringResource(R.string.route_new_route), style = MaterialTheme.typography.labelMedium) }
                    CompactButton(onClick = viewModel::accept) { Text(stringResource(R.string.route_accept), style = MaterialTheme.typography.labelMedium) }
                }
            } else if (uiState.mode == RouteMode.ACTIVE) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    CompactOutlinedButton(onClick = {
                        if (uiState.watchingLocation) viewModel.setWatchingLocation(false)
                        else if (hasLocationPermission) viewModel.setWatchingLocation(true)
                        else permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }) {
                        Text(stringResource(if (uiState.watchingLocation) R.string.route_hide_location else R.string.route_show_location), style = MaterialTheme.typography.labelMedium)
                    }
                    if (uiState.watchingLocation) {
                        CompactOutlinedButton(onClick = { viewModel.setTracking(!uiState.tracking) }) {
                            Text(stringResource(if (uiState.tracking) R.string.route_tracking_on else R.string.route_track), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    val canComplete = !uiState.tracking || uiState.completedWaypointIndex >= route.stations.lastIndex
                    CompactButton(onClick = viewModel::complete, enabled = canComplete) {
                        Text(stringResource(R.string.route_complete_button), style = MaterialTheme.typography.labelMedium)
                    }
                    CompactOutlinedButton(onClick = { pendingDiscard = true }) {
                        Text(stringResource(R.string.route_discard_button), style = MaterialTheme.typography.labelMedium)
                    }
                }
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        value = uiState.nickname,
                        onValueChange = viewModel::setNickname,
                        label = { Text(stringResource(R.string.route_name_label), style = MaterialTheme.typography.labelSmall) },
                        placeholder = { Text(stringResource(R.string.route_name_placeholder), style = MaterialTheme.typography.labelSmall) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = viewModel::saveNickname, enabled = !uiState.nicknameSaving) {
                            Text(stringResource(R.string.route_save_name), style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(
                            onClick = viewModel::saveFavorite,
                            enabled = !uiState.savingFavorite && uiState.nickname.isNotBlank(),
                        ) {
                            Text(stringResource(R.string.route_save_favorite), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = uiState.voiceEnabled, onCheckedChange = viewModel::setVoiceEnabled)
                        Text(stringResource(R.string.route_voice_on), style = MaterialTheme.typography.labelSmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = uiState.keepScreenOn, onCheckedChange = viewModel::setKeepScreenOn)
                        Text(stringResource(R.string.route_keep_screen_on), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            uiState.messageRes?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
private fun TrackProgressChip(uiState: RouteUiState, route: com.routy.app.logic.api.RouteDisplayPayload) {
    val completedCount = if (uiState.completedWaypointIndex < 0) 0 else uiState.completedWaypointIndex + 1
    val nextIdx = (uiState.completedWaypointIndex + 1).coerceIn(0, route.stations.lastIndex)
    val next = route.stations.getOrNull(nextIdx)
    val label = next?.let {
        stringResource(R.string.route_track_progress_chip, completedCount, route.stations.size, it.name ?: "#${it.nodeId}")
    } ?: stringResource(R.string.route_track_progress, completedCount, route.stations.size)
    AssistChip(onClick = {}, label = { Text(label, style = MaterialTheme.typography.labelSmall) })
}

@Composable
private fun ActiveRouteLocationEffect(uiState: RouteUiState, hasLocationPermission: Boolean, viewModel: RouteViewModel) {
    val context = LocalContext.current
    DisposableEffect(uiState.watchingLocation, hasLocationPermission) {
        if (!uiState.watchingLocation || !hasLocationPermission) return@DisposableEffect onDispose {}
        val client = LocationServices.getFusedLocationProviderClient(context)
        val interval = if (uiState.tracking) 3000L else 5000L
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, interval).build()
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
private fun ActiveTrackingEffects(
    uiState: RouteUiState,
    route: com.routy.app.logic.api.RouteDisplayPayload,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FavoritesCard(favorites: List<FavoriteEntry>, loading: Boolean, viewModel: RouteViewModel) {
    var pendingDelete by remember { mutableStateOf<FavoriteEntry?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.route_favorites_title), style = MaterialTheme.typography.labelMedium)
        favorites.forEach { fav ->
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${fav.name} · ${"%.2f".format(fav.display.lengthM / 1000.0)} km",
                    style = MaterialTheme.typography.labelSmall,
                )
                TextButton(onClick = { viewModel.takeFavorite(fav) }, enabled = !loading) {
                    Text(stringResource(R.string.route_favorite_take), style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = { viewModel.toggleShare(fav) }) {
                    Text(
                        stringResource(if (fav.shareToken != null) R.string.route_favorite_unshare else R.string.route_favorite_share),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                if (fav.shareToken != null) {
                    TextButton(onClick = { viewModel.copyFavoriteShareLink(fav) }) {
                        Text(stringResource(R.string.route_favorite_copy_link), style = MaterialTheme.typography.labelSmall)
                    }
                }
                TextButton(onClick = { pendingDelete = fav }) {
                    Text(stringResource(R.string.route_favorite_delete), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
    pendingDelete?.let { fav ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            confirmButton = { TextButton(onClick = { viewModel.deleteFavorite(fav.id); pendingDelete = null }) { Text(stringResource(R.string.route_favorite_delete)) } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.route_cancel)) } },
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
    onSelect: (Int) -> Unit,
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
            label = { Text(label, style = if (dense) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall) },
            textStyle = textStyle,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            sorted.forEach { node ->
                DropdownMenuItem(text = { Text(node.name ?: "#${node.id}", style = textStyle) }, onClick = { onSelect(node.id); expanded = false })
            }
        }
    }
}

private val CompactButtonPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)

@Composable
private fun CompactButton(onClick: () -> Unit, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        contentPadding = CompactButtonPadding,
        modifier = Modifier.heightIn(max = 32.dp),
        content = content,
    )
}

@Composable
private fun CompactOutlinedButton(onClick: () -> Unit, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = CompactButtonPadding,
        modifier = Modifier.heightIn(max = 32.dp),
        content = content,
    )
}
