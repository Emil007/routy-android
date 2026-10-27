package com.routy.app.shell

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.routy.app.recording.ActiveRecordingPanel
import com.routy.app.recording.ConfirmPanel
import com.routy.app.recording.IdlePanel
import com.routy.app.recording.RecordingForegroundService
import com.routy.app.recording.RecordingServiceState
import com.routy.app.recording.RecordingUiState
import com.routy.app.recording.RecordingViewModel
import com.routy.app.logic.recording.RecordingPhase

/**
 * Aufnahme — Sheet-Stufe der Hülle (J4). Die Service-Bindung lebt auf MapChrome-Ebene
 * (eine Quelle der Wahrheit für die eine Karte): dieses Sheet bekommt [service] und
 * [serviceState] gereicht und rendert nur die drei Phasen chromeHosted (kein eigener
 * TopAppBar/Nav, keine eigene Karte — die eine Karte liegt in MapChrome, §7.4).
 *
 * Ablauf wie im alten RecordingScreen, aber ohne Nav-Wechsel:
 *  - IDLE: Starten (Permissions → Foreground-Service)
 *  - RECORDING/PAUSED: Steuerung (Pause/Resume/Stop/Discard)
 *  - CONFIRM: Endpunkte zuordnen, Speichern/Discard
 */
@Composable
fun RecordSheet(
    uiState: RecordingUiState,
    serviceState: RecordingServiceState,
    service: RecordingForegroundService?,
    viewModel: RecordingViewModel,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

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

    Box(modifier = modifier.fillMaxSize()) {
        when {
            serviceState.phase == RecordingPhase.RECORDING || serviceState.phase == RecordingPhase.PAUSED -> {
                ActiveRecordingPanel(
                    uiState = uiState,
                    serviceState = serviceState,
                    onBack = onDone,
                    onPause = { service?.pause() },
                    onResume = { service?.resume() },
                    onStop = {
                        val points = service?.finish().orEmpty()
                        if (points.size >= 2) {
                            // CONFIRM-Phase: die Bindung (MapChrome) holt recordedPoints
                            // und legt sie in den ViewModel — hier nur der Service-Finish.
                        } else {
                            service?.stopAfterCommitOrDiscard()
                        }
                    },
                    onDiscard = { service?.stopAfterCommitOrDiscard() },
                    modifier = Modifier.fillMaxSize(),
                    chromeHosted = true,
                )
            }
            serviceState.phase == RecordingPhase.CONFIRM -> {
                ConfirmPanel(
                    uiState = uiState,
                    viewModel = viewModel,
                    onDiscard = {
                        service?.stopAfterCommitOrDiscard()
                        viewModel.reset()
                    },
                    modifier = Modifier.fillMaxSize(),
                    chromeHosted = true,
                )
            }
            else -> {
                IdlePanel(
                    uiState = uiState,
                    onStart = { requestStart() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
