package com.routy.app.recording

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.routy.app.logic.recording.RecordingPhase

/**
 * Live recording-service binding, extracted from RecordingScreen (J1, §9.4).
 * Same behaviour; called from the old screens now, from MapChrome's shell from J2 on.
 * The service is never stopped on dispose — only unbindService (no stopAll). "Confirm
 * überlebt einen Moduswechsel" (§J2): `serviceState` keeps flowing while the shell lives.
 */
data class RecordingBindingState(
    val service: RecordingForegroundService?,
    val serviceState: RecordingServiceState,
)

@Composable
fun rememberRecordingBinding(viewModel: RecordingViewModel, saved: Boolean, onDone: () -> Unit): RecordingBindingState {
    val context = LocalContext.current
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

    return remember(service, serviceState) { RecordingBindingState(service, serviceState) }
}
