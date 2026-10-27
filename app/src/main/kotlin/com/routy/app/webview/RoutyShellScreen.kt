package com.routy.app.webview

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.routy.app.R
import com.routy.app.RoutyApplication
import com.routy.app.core.GpxUploadNotifier
import com.routy.app.recording.RecordingActiveBanner
import com.routy.app.shell.MapChrome
import com.routy.app.update.UpdateBanner
import com.routy.app.upload.PendingUploadBanner

/**
 * Post-Login-Hülle (J2): dünne Shell, die MapChrome hostet. Navigation/Moduswechsel
 * liegen im Dock (MapChrome), Profil/Stats/Settings/Admin sind Sheet-Stufen.
 * Die schwebenden Banner bleiben hier oben (unabhängig vom Modus).
 */
@Composable
fun RoutyShellScreen(onSignedOut: () -> Unit, onStartRecording: () -> Unit) {
    val app = LocalContext.current.applicationContext as RoutyApplication
    val viewModel: ShellViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ShellViewModel(app.secureStorage, app.apiClientProvider, app.bootstrapLoader, app.networkCache) }
        },
    )
    val uiState by viewModel.uiState.collectAsState()

    val baseUrl = app.secureStorage.serverUrl

    LaunchedEffect(uiState.signedOut) {
        if (uiState.signedOut) {
            baseUrl?.let { CookieBridge.clearSessionCookie(it) }
            onSignedOut()
        }
    }

    LaunchedEffect(Unit) {
        val token = app.secureStorage.token
        if (baseUrl != null && token != null) {
            CookieBridge.installSessionCookie(baseUrl, token)
        }
    }

    if (baseUrl == null || uiState.signedOut) return // about to navigate away via onSignedOut

    val snackbarHostState = remember { SnackbarHostState() }
    val gpxUploadedMessage = stringResource(R.string.record_upload_completed_background)

    LaunchedEffect(Unit) {
        GpxUploadNotifier.successes.collect {
            snackbarHostState.showSnackbar(gpxUploadedMessage)
        }
    }

    LaunchedEffect(app) {
        GpxUploadNotifier.failures.collect { httpCode ->
            snackbarHostState.showSnackbar(app.getString(R.string.shell_upload_failed_snackbar, httpCode))
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Schwebende Banner unabhängig vom Modus (Sideload-Verteilung)
            UpdateBanner()
            PendingUploadBanner()
            RecordingActiveBanner(onOpenRecording = onStartRecording)
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                MapChrome(
                    shellViewModel = viewModel,
                    onStartRecording = onStartRecording,
                    accountLocaleTag = uiState.user?.locale.orEmpty(),
                    modifier = Modifier.fillMaxSize().zIndex(0f),
                )
            }
        }
    }
}
