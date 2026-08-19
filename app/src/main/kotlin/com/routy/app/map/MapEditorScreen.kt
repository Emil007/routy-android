package com.routy.app.map

import android.os.Bundle
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.routy.app.R
import com.routy.app.webview.CookieBridge
import com.routy.app.webview.RoutyWebView

/**
 * Full-screen web map editor (`/map`) from the native Map tab. Session cookie is installed before
 * load so the page runs in embedded mode (`client=app`, no NavBar). Back pops to native Map.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapEditorScreen(
    baseUrl: String,
    token: String,
    onBack: () -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mapUrl = "$baseUrl/map"
    val webViewState = remember { mutableStateOf<Bundle?>(null) }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(baseUrl, token) {
        CookieBridge.installSessionCookie(baseUrl, token)
        onDispose { }
    }

    BackHandler {
        val webView = webViewRef.value
        if (webView?.canGoBack() == true) {
            webView.goBack()
        } else {
            onBack()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.map_edit_network)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_close))
                    }
                },
            )
        },
    ) { padding ->
        RoutyWebView(
            url = mapUrl,
            onNavigateToLogin = onNavigateToLogin,
            modifier = Modifier.fillMaxSize().padding(padding),
            restoredState = webViewState.value,
            onStateSnapshot = { webViewState.value = it },
            onWebViewReady = { webViewRef.value = it },
        )
    }
}
