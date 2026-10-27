package com.routy.app.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.routy.app.R
import com.routy.app.RoutyApplication
import com.routy.app.stats.StatsViewModel

/**
 * Profil-Stufe der Hülle (§J5 / Android-Plan 3.9): Name, Punkte + Kurz-Statistik,
 * Einstellungen, Konto (Web-Stufe), Admin (Web-Stufe, nur admin), Abmelden.
 * Die Punktzahl kommt aus demselben StatsViewModel wie die Stats-Stufe (§4.2 —
 * ViewModel am NavBackStackEntry der Shell wird geteilt).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileMenuSheet(
    displayName: String,
    isAdmin: Boolean,
    scoreLine: String?,
    onStats: () -> Unit,
    onSettings: () -> Unit,
    onAccount: () -> Unit,
    onAdmin: () -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(displayName, style = MaterialTheme.typography.titleMedium)
            if (scoreLine != null) {
                Text(
                    scoreLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onStats, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.nav_stats))
            }
            Button(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.nav_settings))
            }
            Button(onClick = onAccount, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.nav_account))
            }
            if (isAdmin) {
                Button(onClick = onAdmin, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.nav_admin))
                }
            }
            OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_logout))
            }
        }
    }
}

/** Punktzahl- + Statistik-Zeile für die Profil-Stufe, aus dem geteilten StatsViewModel. */
@Composable
fun rememberProfileScoreLine(): String? {
    val app = LocalContext.current.applicationContext as RoutyApplication
    val viewModel: StatsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { StatsViewModel(app.apiClientProvider, app.networkCache, app.bootstrapLoader, app.applicationContext) }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    val points = uiState.points?.totalPoints
    val walks = uiState.stats?.walkCount
    if (points == null && walks == null) return null
    val pointsText = points?.toString() ?: "–"
    val walksText = walks?.toString() ?: "–"
    return "${stringResource(R.string.stats_points)}: $pointsText · ${stringResource(R.string.stats_walks)}: $walksText"
}
