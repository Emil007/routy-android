package com.routy.app.route

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Looper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.routy.app.map.BaseMapStyle
import com.routy.app.map.RoutyMapView
import java.text.Collator

@Composable
fun RouteScreen(modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as RoutyApplication
    val baseUrl = app.secureStorage.serverUrl.orEmpty()
    val viewModel: RouteViewModel = viewModel(
        factory = viewModelFactory {
            initializer { RouteViewModel(app.apiClientProvider, baseUrl) }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(uiState.pendingShareUrl) {
        val url = uiState.pendingShareUrl ?: return@LaunchedEffect
        clipboard.setText(AnnotatedString(url))
        viewModel.clearPendingShareUrl()
    }

    if (uiState.loadingInitial) {
        Column(modifier = modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(modifier = modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (uiState.mode == RouteMode.SUGGESTING && uiState.favorites.isNotEmpty()) {
            item { FavoritesCard(uiState.favorites, uiState.status == RouteStatus.LOADING, viewModel) }
        }

        if (uiState.mode == RouteMode.SUGGESTING) {
            item { SuggestForm(uiState, viewModel) }
        }

        if (uiState.mode == RouteMode.ACTIVE) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.route_active_notice),
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            item {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.nickname,
                        onValueChange = viewModel::setNickname,
                        placeholder = { Text(stringResource(R.string.route_nickname_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = viewModel::saveNickname, enabled = !uiState.nicknameSaving) {
                        Text(stringResource(R.string.common_save))
                    }
                }
            }
        }

        uiState.messageRes?.let { res ->
            item { Text(stringResource(res), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) }
        }

        uiState.route?.let { route ->
            item {
                RouteResultCard(
                    uiState = uiState,
                    route = route,
                    nodes = uiState.nodes,
                    viewModel = viewModel,
                )
            }
        }
    }
}

@Composable
private fun FavoritesCard(favorites: List<FavoriteEntry>, loading: Boolean, viewModel: RouteViewModel) {
    var pendingDelete by remember { mutableStateOf<FavoriteEntry?>(null) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.route_favorites_title), style = MaterialTheme.typography.titleSmall)
            favorites.forEach { fav ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "${fav.name} — ${"%.2f".format(fav.display.lengthM / 1000.0)} ${stringResource(R.string.common_km)}",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = { viewModel.takeFavorite(fav) }, enabled = !loading) {
                        Text(stringResource(R.string.route_favorite_take))
                    }
                    TextButton(onClick = { viewModel.toggleShare(fav) }) {
                        Text(stringResource(if (fav.shareToken != null) R.string.route_favorite_unshare else R.string.route_favorite_share))
                    }
                    TextButton(onClick = { pendingDelete = fav }) {
                        Text(stringResource(R.string.route_favorite_delete))
                    }
                }
            }
        }
    }

    pendingDelete?.let { fav ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteFavorite(fav.id); pendingDelete = null }) { Text(stringResource(R.string.route_favorite_delete)) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.route_cancel)) } },
            text = { Text(stringResource(R.string.route_favorite_delete_confirm)) },
        )
    }
}

@Composable
private fun SuggestForm(uiState: RouteUiState, viewModel: RouteViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            NodeDropdown(
                label = stringResource(R.string.route_start),
                nodes = uiState.nodes,
                selectedId = uiState.startNodeId,
                onSelect = viewModel::setStartNodeId,
            )

            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = uiState.isLoop, onCheckedChange = viewModel::setIsLoop)
                Text(stringResource(R.string.route_loop))
            }

            if (!uiState.isLoop) {
                NodeDropdown(
                    label = stringResource(R.string.route_destination),
                    nodes = uiState.nodes,
                    selectedId = uiState.destinationNodeId,
                    onSelect = viewModel::setDestinationNodeId,
                )
            }

            NodeDropdown(
                label = "${stringResource(R.string.route_waypoint)} (${stringResource(R.string.common_optional)})",
                nodes = uiState.nodes,
                selectedId = uiState.waypointNodeId,
                onSelect = viewModel::setWaypointNodeId,
                noneLabel = stringResource(R.string.route_waypoint_none),
            )

            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = uiState.explorerMode, onCheckedChange = viewModel::setExplorerMode)
                Text(stringResource(R.string.route_explorer_mode))
            }
            Text(
                stringResource(R.string.route_explorer_mode_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val loading = uiState.status == RouteStatus.LOADING
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.suggest() }, enabled = !loading && uiState.startNodeId != null) {
                    Text(stringResource(if (loading) R.string.route_generating else R.string.route_suggest))
                }
                OutlinedButton(onClick = { viewModel.suggest("short") }, enabled = !loading && uiState.startNodeId != null) {
                    Text(stringResource(R.string.route_preset_short))
                }
                OutlinedButton(onClick = { viewModel.suggest("long") }, enabled = !loading && uiState.startNodeId != null) {
                    Text(stringResource(R.string.route_preset_long))
                }
            }
        }
    }
}

@Composable
private fun RouteResultCard(uiState: RouteUiState, route: com.routy.app.logic.api.RouteDisplayPayload, nodes: List<NodeDto>, viewModel: RouteViewModel) {
    val context = LocalContext.current
    var hasLocationPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasLocationPermission = granted
        if (granted) viewModel.setWatchingLocation(true)
    }

    DisposableEffect(uiState.watchingLocation, hasLocationPermission) {
        if (!uiState.watchingLocation || !hasLocationPermission) return@DisposableEffect onDispose {}
        val client = LocationServices.getFusedLocationProviderClient(context)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L).build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { viewModel.setMyLocation(GeoPoint(it.latitude, it.longitude)) }
            }
        }
        requestLocationUpdatesIfPermitted(context, client, request, callback)
        onDispose { client.removeLocationUpdates(callback) }
    }

    var showDiscardConfirm by remember { mutableStateOf(false) }
    val loading = uiState.status == RouteStatus.LOADING

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            RoutyMapView(
                style = BaseMapStyle.STREETS,
                nodes = nodes,
                segments = uiState.segments,
                routeGeometry = route.geometry,
                stations = route.stations,
                myLocation = uiState.myLocation,
                fitKey = uiState.token.ifEmpty { route.nodeChain.joinToString("-") },
                modifier = Modifier.fillMaxWidth().height(280.dp),
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = {
                    Text("${stringResource(R.string.route_distance_label)}: ${"%.2f".format(route.lengthM / 1000.0)} ${stringResource(R.string.common_km)}")
                })
                AssistChip(onClick = {}, label = {
                    Text("${stringResource(R.string.route_duration_label)}: ${route.durationMin} ${stringResource(R.string.common_min)}")
                })
                route.elevation?.let { elevation ->
                    AssistChip(onClick = {}, label = { Text("↗ ${stringResource(R.string.route_elevation_gain, elevation.gainM)}") })
                    AssistChip(onClick = {}, label = { Text("↘ ${stringResource(R.string.route_elevation_loss, elevation.lossM)}") })
                }
            }

            Column {
                Text(
                    stringResource(R.string.route_station_list),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    route.shortStationGroups.joinToString(" › ") { group ->
                        if (group.viaSegmentName != null) "${group.text} (${stringResource(R.string.route_via, group.viaSegmentName)})" else group.text
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (uiState.mode == RouteMode.SUGGESTING) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.adjust("shorter") }, enabled = !loading) { Text(stringResource(R.string.route_shorter)) }
                    OutlinedButton(onClick = { viewModel.adjust("longer") }, enabled = !loading) { Text(stringResource(R.string.route_longer)) }
                    OutlinedButton(onClick = viewModel::another, enabled = !loading) { Text(stringResource(R.string.route_new_route)) }
                    Button(onClick = viewModel::accept, enabled = !loading) { Text(stringResource(R.string.route_accept)) }
                    OutlinedButton(onClick = viewModel::cancel, enabled = !loading) { Text(stringResource(R.string.route_cancel)) }
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.favoriteNameInput,
                        onValueChange = viewModel::setFavoriteNameInput,
                        placeholder = { Text(stringResource(R.string.route_favorite_name_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = viewModel::saveFavorite,
                        enabled = !uiState.savingFavorite && uiState.favoriteNameInput.isNotBlank(),
                    ) { Text(stringResource(R.string.route_save_favorite)) }
                }
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        if (uiState.watchingLocation) {
                            viewModel.setWatchingLocation(false)
                        } else if (hasLocationPermission) {
                            viewModel.setWatchingLocation(true)
                        } else {
                            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        }
                    }) {
                        Text(stringResource(if (uiState.watchingLocation) R.string.route_hide_location else R.string.route_show_location))
                    }
                    Button(onClick = viewModel::complete, enabled = !loading) { Text(stringResource(R.string.route_complete_button)) }
                    OutlinedButton(onClick = { showDiscardConfirm = true }, enabled = !loading) { Text(stringResource(R.string.route_discard_button)) }
                }
            }
        }
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            confirmButton = {
                TextButton(onClick = { viewModel.discardActive(); showDiscardConfirm = false }) { Text(stringResource(R.string.route_discard_button)) }
            },
            dismissButton = { TextButton(onClick = { showDiscardConfirm = false }) { Text(stringResource(R.string.route_cancel)) } },
            text = { Text(stringResource(R.string.route_discard_confirm)) },
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
) {
    var expanded by remember { mutableStateOf(false) }
    val collator = remember { Collator.getInstance() }
    val sorted = remember(nodes) { nodes.sortedWith(compareBy(collator) { it.name ?: "#${it.id}" }) }
    val selectedLabel = sorted.firstOrNull { it.id == selectedId }?.let { it.name ?: "#${it.id}" } ?: (noneLabel ?: "")

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            sorted.forEach { node ->
                DropdownMenuItem(text = { Text(node.name ?: "#${node.id}") }, onClick = { onSelect(node.id); expanded = false })
            }
        }
    }
}
