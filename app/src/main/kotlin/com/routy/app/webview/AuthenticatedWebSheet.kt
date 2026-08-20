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
    val url = "${baseUrl.trimEnd('/')}$path"

    LaunchedEffect(baseUrl, token) {
        CookieBridge.installSessionCookie(baseUrl.trimEnd('/'), token)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        RoutyWebView(
            url = url,
            onNavigateToLogin = {
                onDismiss()
                onNavigateToLogin()
            },
            modifier = Modifier.fillMaxWidth().height(560.dp),
        )
    }
}
