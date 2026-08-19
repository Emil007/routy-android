package com.routy.app.recording

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.routy.app.R
import com.routy.app.RoutyApplication
import com.routy.app.logic.api.GeoPoint
import com.routy.app.logic.recording.EndpointDecision
import com.routy.app.logic.recording.NodeCandidate
import com.routy.app.logic.recording.RecordingPhase
import com.routy.app.map.BaseMapStyle
import com.routy.app.map.MapStyleSwitcher
import com.routy.app.map.RoutyMapView

private val CompactPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as RoutyApplication
    val viewModel: RecordingViewModel = viewModel(
        factory = viewModelFactory { initializer { RecordingViewModel(app.apiClientProvider, app.gpxCommitScheduler, app.recordingConfirmStore) } },
    )
    val uiState by viewModel.uiState.collectAsState()

    var service by remember { mutableStateOf<RecordingForegroundService?>(null) }
    var serviceState by remember { mutableStateOf(RecordingServiceState()) }

    val serviceConnection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                service = (binder as RecordingForegroundService.LocalBinder).getService()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
            }
        }
    }

    DisposableEffect(Unit) {
        context.bindService(Intent(context, RecordingForegroundService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
        onDispose { context.unbindService(serviceConnection) }
    }

    LaunchedEffect(service) {
        service?.state?.collect { serviceState = it }
    }

    LaunchedEffect(service, serviceState.phase) {
        if (serviceState.phase == RecordingPhase.CONFIRM) {
            service?.recordedPoints()?.let { points ->
                if (points.size >= 2) viewModel.setRecordedPoints(points)
            }
        }
    }

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) {
            service?.stopAfterCommitOrDiscard()
            viewModel.reset()
            onDone()
        }
    }

    fun needsNotificationPermission() =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        context.startForegroundService(Intent(context, RecordingForegroundService::class.java))
    }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            if (needsNotificationPermission()) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                context.startForegroundService(Intent(context, RecordingForegroundService::class.java))
            }
        }
    }

    fun requestStart() {
        when {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ->
                locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            needsNotificationPermission() -> notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> context.startForegroundService(Intent(context, RecordingForegroundService::class.java))
        }
    }

    when {
        serviceState.phase == RecordingPhase.RECORDING || serviceState.phase == RecordingPhase.PAUSED -> {
            ActiveRecordingFullscreen(
                uiState = uiState,
                serviceState = serviceState,
                onBack = onDone,
                onPause = { service?.pause() },
                onResume = { service?.resume() },
                onStop = {
                    val points = service?.finish().orEmpty()
                    if (points.size >= 2) {
                        viewModel.setRecordedPoints(points)
                    } else {
                        service?.stopAfterCommitOrDiscard()
                    }
                },
                onDiscard = { service?.stopAfterCommitOrDiscard() },
                modifier = modifier.fillMaxSize(),
            )
        }
        serviceState.phase == RecordingPhase.CONFIRM -> {
            ConfirmFullscreen(
                uiState = uiState,
                viewModel = viewModel,
                onBack = onDone,
                onDiscard = {
                    service?.stopAfterCommitOrDiscard()
                    viewModel.reset()
                },
                modifier = modifier.fillMaxSize(),
            )
        }
        else -> Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.record_title)) },
                    navigationIcon = {
                        IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = modifier.fillMaxWidth().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BatteryOptimizationPrompt()
                Text(stringResource(R.string.record_instructions), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.record_background_capability),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { requestStart() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.record_start))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmFullscreen(
    uiState: RecordingUiState,
    viewModel: RecordingViewModel,
    onBack: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var mapStyle by remember { mutableStateOf(BaseMapStyle.STREETS) }
    val trackGeometry = remember(uiState.points) {
        uiState.points.map { GeoPoint(it.lat, it.lng) }
    }

    Box(modifier = modifier) {
        RoutyMapView(
            style = mapStyle,
            nodes = uiState.nodes,
            segments = emptyList(),
            routeGeometry = trackGeometry,
            stations = emptyList(),
            myLocation = null,
            fitKey = uiState.points.size,
            routeColor = "#9a3b29",
            modifier = Modifier.fillMaxSize(),
        )

        FloatingMapChrome(onBack = onBack, mapStyle = mapStyle, onMapStyle = { mapStyle = it })

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp),
            tonalElevation = 3.dp,
            shadowElevation = 4.dp,
            shape = MaterialTheme.shapes.medium,
        ) {
            ConfirmSection(
                uiState = uiState,
                viewModel = viewModel,
                onDiscard = onDiscard,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveRecordingFullscreen(
    uiState: RecordingUiState,
    serviceState: RecordingServiceState,
    onBack: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var mapStyle by remember { mutableStateOf(BaseMapStyle.STREETS) }

    Box(modifier = modifier) {
        RoutyMapView(
            style = mapStyle,
            nodes = uiState.nodes,
            segments = emptyList(),
            routeGeometry = emptyList(),
            stations = emptyList(),
            myLocation = serviceState.currentPosition,
            fitKey = serviceState.pointCount,
            routeColor = "#9a3b29",
            modifier = Modifier.fillMaxSize(),
        )

        FloatingMapChrome(onBack = onBack, mapStyle = mapStyle, onMapStyle = { mapStyle = it })

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp),
            tonalElevation = 3.dp,
            shadowElevation = 4.dp,
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (serviceState.locationError) {
                    Text(
                        stringResource(R.string.record_location_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(onClick = {}, label = { Text(stringResource(R.string.record_points_so_far, serviceState.pointCount), style = MaterialTheme.typography.labelSmall) })
                    AssistChip(onClick = {}, label = {
                        Text(
                            "${stringResource(R.string.record_distance_so_far)}: ${"%.2f".format(serviceState.lengthM / 1000.0)} ${stringResource(R.string.common_km)}",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    })
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (serviceState.phase == RecordingPhase.RECORDING) {
                        CompactOutlined(onPause) { Text(stringResource(R.string.record_pause), style = MaterialTheme.typography.labelMedium) }
                    } else {
                        CompactOutlined(onResume) { Text(stringResource(R.string.record_resume), style = MaterialTheme.typography.labelMedium) }
                    }
                    CompactPrimary(onStop) { Text(stringResource(R.string.record_stop), style = MaterialTheme.typography.labelMedium) }
                    if (serviceState.phase == RecordingPhase.PAUSED) {
                        CompactOutlined(onDiscard) { Text(stringResource(R.string.record_discard), style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingMapChrome(onBack: () -> Unit, mapStyle: BaseMapStyle, onMapStyle: (BaseMapStyle) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), MaterialTheme.shapes.small),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
        }
        MapStyleSwitcher(selected = mapStyle, onSelect = onMapStyle)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConfirmSection(
    uiState: RecordingUiState,
    viewModel: RecordingViewModel,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val start = uiState.points.firstOrNull()
    val end = uiState.points.lastOrNull()
    val startDecision = uiState.startDecision
    val endDecision = uiState.endDecision

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            if (start != null && startDecision != null) {
                CompactEndpointBlock(
                    label = stringResource(R.string.record_start_node),
                    candidates = viewModel.startCandidates(),
                    decision = startDecision,
                    onDecisionChange = viewModel::setStartDecision,
                    modifier = Modifier.weight(1f),
                )
            }
            if (end != null && endDecision != null) {
                CompactEndpointBlock(
                    label = stringResource(R.string.record_end_node),
                    candidates = viewModel.endCandidates(),
                    decision = endDecision,
                    onDecisionChange = viewModel::setEndDecision,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = uiState.markStartAsHome, onCheckedChange = viewModel::setMarkStartAsHome)
            Text(stringResource(R.string.record_mark_as_home), style = MaterialTheme.typography.labelSmall)
        }

        uiState.messageRes?.let { res ->
            Text(
                stringResource(res),
                color = if (uiState.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall,
            )
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CompactPrimary(onClick = viewModel::save, enabled = !uiState.saving) {
                Text(stringResource(if (uiState.saving) R.string.record_saving else R.string.record_save), style = MaterialTheme.typography.labelMedium)
            }
            CompactOutlined(onDiscard, enabled = !uiState.saving) {
                Text(stringResource(R.string.record_discard), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun CompactEndpointBlock(
    label: String,
    candidates: List<NodeCandidate>,
    decision: EndpointDecision,
    onDecisionChange: (EndpointDecision) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isExisting = decision is EndpointDecision.Existing
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(
                selected = isExisting,
                enabled = candidates.isNotEmpty(),
                onClick = { onDecisionChange(EndpointDecision.Existing(candidates.first().id)) },
                label = { Text(stringResource(R.string.record_use_existing), style = MaterialTheme.typography.labelSmall) },
            )
            FilterChip(
                selected = !isExisting,
                onClick = { onDecisionChange(EndpointDecision.NewJunction()) },
                label = { Text(stringResource(R.string.record_create_new), style = MaterialTheme.typography.labelSmall) },
            )
        }
        when (decision) {
            is EndpointDecision.Existing -> CandidateDropdown(candidates, decision.nodeId) { onDecisionChange(EndpointDecision.Existing(it)) }
            is EndpointDecision.NewJunction -> {
                OutlinedTextField(
                    value = decision.part1,
                    onValueChange = { onDecisionChange(decision.copy(part1 = it)) },
                    placeholder = { Text(stringResource(R.string.record_name_part1), style = MaterialTheme.typography.labelSmall) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CandidateDropdown(candidates: List<NodeCandidate>, selectedId: Int?, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = candidates.firstOrNull { it.id == selectedId }
    val label = selected?.let { "${it.name ?: "#${it.id}"} (${it.distanceM.toInt()} m)" } ?: ""

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            enabled = candidates.isNotEmpty(),
            textStyle = MaterialTheme.typography.labelSmall,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            candidates.forEach { candidate ->
                DropdownMenuItem(
                    text = { Text("${candidate.name ?: "#${candidate.id}"} (${candidate.distanceM.toInt()} m)", style = MaterialTheme.typography.labelSmall) },
                    onClick = { onSelect(candidate.id); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun CompactPrimary(onClick: () -> Unit, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    Button(onClick = onClick, enabled = enabled, contentPadding = CompactPadding, modifier = Modifier.heightIn(max = 32.dp), content = content)
}

@Composable
private fun CompactOutlined(onClick: () -> Unit, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, contentPadding = CompactPadding, modifier = Modifier.heightIn(max = 32.dp), content = content)
}
