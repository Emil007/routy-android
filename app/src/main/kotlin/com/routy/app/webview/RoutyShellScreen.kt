package com.routy.app.webview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.routy.app.R
import com.routy.app.RoutyApplication
import com.routy.app.route.RouteScreen
import com.routy.app.update.UpdateBanner

private data class ShellTab(val labelRes: Int, val path: String, val icon: ImageVector, val adminOnly: Boolean = false)

private val TABS = listOf(
    ShellTab(R.string.nav_route, "route", Icons.AutoMirrored.Filled.DirectionsWalk),
    ShellTab(R.string.nav_map, "map", Icons.Filled.Map),
    ShellTab(R.string.nav_stats, "stats", Icons.Filled.BarChart),
    ShellTab(R.string.nav_settings, "settings", Icons.Filled.Settings),
    ShellTab(R.string.nav_admin, "admin", Icons.Filled.AdminPanelSettings, adminOnly = true),
)

/**
 * Bottom nav shared across the whole post-login app. Route is native (RouteScreen, M3) — every
 * other section (see the architecture note in NOTES.md at the repo root) stays a WebView tab
 * reusing the website as-is, since editing-heavy/occasional screens gain nothing from a native
 * rebuild.
 */
@Composable
fun RoutyShellScreen(onSignedOut: () -> Unit, onStartRecording: () -> Unit) {
    val app = LocalContext.current.applicationContext as RoutyApplication
    val viewModel: ShellViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ShellViewModel(app.secureStorage, app.apiClientProvider) }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

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

    val visibleTabs = TABS.filter { !it.adminOnly || uiState.user?.role == "admin" }
    val currentTab = visibleTabs.getOrElse(selectedTab.coerceIn(0, visibleTabs.lastIndex)) { visibleTabs.first() }

    Scaffold(
        topBar = {
            // Route already has its own top bar; webview tabs don't.
            if (currentTab.path != "route") {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    actions = {
                        IconButton(onClick = { viewModel.signOut() }) {
                            Icon(Icons.Filled.ExitToApp, contentDescription = null)
                        }
                    },
                )
            }
        },
        bottomBar = {
            NavigationBar {
                visibleTabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { selectedTab = index },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.labelRes)) },
                    )
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Sideload-only distribution means no auto-update — worth surfacing regardless of
            // which tab is open, not just on one screen. weight(1f) on the tab content below
            // (not fillMaxSize()) so the banner keeps its own natural height and the tab content
            // takes only what's left, rather than both fighting over the full column height.
            UpdateBanner()
            if (currentTab.path == "route") {
                RouteScreen(onStartRecording = onStartRecording, modifier = Modifier.weight(1f))
            } else {
                RoutyWebView(
                    url = "$baseUrl/${currentTab.path}",
                    onNavigateToLogin = { viewModel.signOut() },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
