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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.routy.app.logic.recording.EndpointDecision
import com.routy.app.logic.recording.NodeCandidate
import com.routy.app.logic.recording.RecordingPhase
import com.routy.app.map.BaseMapStyle
import com.routy.app.map.RoutyMapView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as RoutyApplication
    val viewModel: RecordingViewModel = viewModel(
        factory = viewModelFactory { initializer { RecordingViewModel(app.apiClientProvider) } },
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
        // Bind only, so the service exists (and reports its real phase — RECORDING/PAUSED if the
        // user already started one and navigated away) without forcing it to start recording just
        // because this screen was opened. startForegroundService() only happens from the Start button.
        context.bindService(Intent(context, RecordingForegroundService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
        onDispose { context.unbindService(serviceConnection) }
    }

    LaunchedEffect(service) {
        service?.state?.collect { serviceState = it }
    }

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) {
            service?.stopAfterCommitOrDiscard()
            viewModel.reset()
            onDone()
        }
    }

    // Chained one-at-a-time rather than firing both launchers from one click: Activity Result
    // launchers aren't safe to invoke back-to-back in the same call — each step only proceeds to
    // the next once its own callback fires. Location is required (gates starting at all);
    // notifications are best-effort (recording still works without it, just no visible ongoing
    // notification), so its callback starts the service unconditionally either way.
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.record_title)) },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.Filled.ArrowBack, contentDescription = null) }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = modifier.fillMaxWidth().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                serviceState.phase == RecordingPhase.CONFIRM -> {
                    item {
                        ConfirmSection(
                            uiState = uiState,
                            viewModel = viewModel,
                            onDiscard = {
                                service?.stopAfterCommitOrDiscard()
                                viewModel.reset()
                            },
                        )
                    }
                }
                serviceState.phase == RecordingPhase.RECORDING || serviceState.phase == RecordingPhase.PAUSED -> {
                    item {
                        ActiveRecordingSection(
                            uiState = uiState,
                            serviceState = serviceState,
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
                        )
                    }
                }
                else -> {
                    item {
                        Text(stringResource(R.string.record_instructions), style = MaterialTheme.typography.bodyMedium)
                    }
                    item {
                        Text(
                            stringResource(R.string.record_background_capability),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    item {
                        Button(onClick = { requestStart() }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.record_start))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveRecordingSection(
    uiState: RecordingUiState,
    serviceState: RecordingServiceState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onDiscard: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            RoutyMapView(
                style = BaseMapStyle.STREETS,
                nodes = uiState.nodes,
                segments = emptyList(),
                routeGeometry = emptyList(),
                stations = emptyList(),
                myLocation = serviceState.currentPosition,
                fitKey = null,
                routeColor = "#9a3b29",
                modifier = Modifier.fillMaxWidth().height(280.dp),
            )

            if (serviceState.locationError) {
                Text(
                    stringResource(R.string.record_location_error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${stringResource(R.string.record_points_so_far, serviceState.pointCount)}", style = MaterialTheme.typography.bodySmall)
                Text(
                    "${stringResource(R.string.record_distance_so_far)}: ${"%.2f".format(serviceState.lengthM / 1000.0)} ${stringResource(R.string.common_km)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (serviceState.phase == RecordingPhase.RECORDING) {
                    OutlinedButton(onClick = onPause) { Text(stringResource(R.string.record_pause)) }
                } else {
                    OutlinedButton(onClick = onResume) { Text(stringResource(R.string.record_resume)) }
                }
                Button(onClick = onStop) { Text(stringResource(R.string.record_stop)) }
                if (serviceState.phase == RecordingPhase.PAUSED) {
                    OutlinedButton(onClick = onDiscard) { Text(stringResource(R.string.record_discard)) }
                }
            }
        }
    }
}

@Composable
private fun ConfirmSection(uiState: RecordingUiState, viewModel: RecordingViewModel, onDiscard: () -> Unit) {
    val start = uiState.points.firstOrNull()
    val end = uiState.points.lastOrNull()
    val startDecision = uiState.startDecision
    val endDecision = uiState.endDecision

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.record_confirm_title), style = MaterialTheme.typography.titleMedium)

            if (start != null && startDecision != null) {
                EndpointDecisionSection(
                    label = stringResource(R.string.record_start_node),
                    candidates = viewModel.startCandidates(),
                    decision = startDecision,
                    onDecisionChange = viewModel::setStartDecision,
                )
            }

            Row {
                Checkbox(checked = uiState.markStartAsHome, onCheckedChange = viewModel::setMarkStartAsHome)
                Text(stringResource(R.string.record_mark_as_home))
            }

            if (end != null && endDecision != null) {
                EndpointDecisionSection(
                    label = stringResource(R.string.record_end_node),
                    candidates = viewModel.endCandidates(),
                    decision = endDecision,
                    onDecisionChange = viewModel::setEndDecision,
                )
            }

            uiState.messageRes?.let { res ->
                Text(
                    stringResource(res),
                    color = if (uiState.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::save, enabled = !uiState.saving) {
                    Text(stringResource(if (uiState.saving) R.string.record_saving else R.string.record_save))
                }
                OutlinedButton(onClick = onDiscard, enabled = !uiState.saving) {
                    Text(stringResource(R.string.record_discard))
                }
            }
        }
    }
}

@Composable
private fun EndpointDecisionSection(
    label: String,
    candidates: List<NodeCandidate>,
    decision: EndpointDecision,
    onDecisionChange: (EndpointDecision) -> Unit,
) {
    val isExisting = decision is EndpointDecision.Existing
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = isExisting,
                enabled = candidates.isNotEmpty(),
                onClick = { onDecisionChange(EndpointDecision.Existing(candidates.first().id)) },
                label = { Text(stringResource(R.string.record_use_existing)) },
            )
            FilterChip(
                selected = !isExisting,
                onClick = { onDecisionChange(EndpointDecision.NewJunction()) },
                label = { Text(stringResource(R.string.record_create_new)) },
            )
        }
        when (decision) {
            is EndpointDecision.Existing -> CandidateDropdown(candidates, decision.nodeId) { onDecisionChange(EndpointDecision.Existing(it)) }
            is EndpointDecision.NewJunction -> {
                OutlinedTextField(
                    value = decision.part1,
                    onValueChange = { onDecisionChange(decision.copy(part1 = it)) },
                    placeholder = { Text(stringResource(R.string.record_name_part1)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = decision.part2,
                    onValueChange = { onDecisionChange(decision.copy(part2 = it)) },
                    placeholder = { Text(stringResource(R.string.record_name_part2)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun CandidateDropdown(candidates: List<NodeCandidate>, selectedId: Int?, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = candidates.firstOrNull { it.id == selectedId }
    Column {
        OutlinedButton(onClick = { expanded = true }, enabled = candidates.isNotEmpty()) {
            Text(
                selected?.let { "${it.name ?: "#${it.id}"} (${it.distanceM.toInt()} m)" } ?: "",
            )
        }
        if (expanded) {
            Column {
                candidates.forEach { candidate ->
                    OutlinedButton(onClick = { onSelect(candidate.id); expanded = false }, modifier = Modifier.fillMaxWidth()) {
                        Text("${candidate.name ?: "#${candidate.id}"} (${candidate.distanceM.toInt()} m)")
                    }
                }
            }
        }
    }
}
