package com.routy.app.webview

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** In-app WebView with a short-lived routy_web session — same auth as the Admin tab, not Chrome Custom Tabs. */
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
    val context = LocalContext.current

    // M36/F52: KEIN Bearer-Cookie — der WebView holt eine 15-Minuten-routy_web-Session
    // über POST /api/auth/web-session (Bearer nur im Header der nativen App) und bekommt
    // nur deren Set-Cookie. Eine alte routy_session aus früheren Installationen wird gelöscht.
    LaunchedEffect(baseUrl) {
        val bridge = WebSessionBridge(context)
        val cookie = bridge.obtainWebCookie()
        CookieBridge.clearLegacySessionCookie(trimmedBase)
        if (cookie != null) {
            CookieBridge.installSessionCookie(trimmedBase, cookie)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        RoutyWebView(
            url = url,
            allowedHost = allowedHost,
            // M36: /login im WebView schließt nur die Web-Stufe — die App-Sitzung
            // (Bearer) bleibt bestehen; das nächste Öffnen holt eine neue routy_web.
            onNavigateToLogin = {
                onDismiss()
            },
            // J6: /map und /route in der Web-Stufe schließen die Stufe (native Karte),
            // statt den Web-Atlas in den WebView zu laden.
            onDismiss = onDismiss,
            // J5: keine feste 560dp — Höhe übernimmt das ModalSheet (Drittel bis Vollbild).
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
