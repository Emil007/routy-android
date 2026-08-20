package com.routy.app.webview

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** In-app WebView with session cookie — same auth as Admin tab, not Chrome Custom Tabs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticatedWebSheet(
    baseUrl: String,
    token: String,
    path: String,
    onDismiss: () -> Unit,
    onNavigateToLogin: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val trimmedBase = baseUrl.trimEnd('/')
    val url = "$trimmedBase$path"
    val allowedHost = hostFromBaseUrl(trimmedBase) ?: return

    LaunchedEffect(baseUrl, token) {
        CookieBridge.installSessionCookie(trimmedBase, token)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        RoutyWebView(
            url = url,
            allowedHost = allowedHost,
            onNavigateToLogin = {
                onDismiss()
                onNavigateToLogin()
            },
            modifier = Modifier.fillMaxWidth().height(560.dp),
        )
    }
}
