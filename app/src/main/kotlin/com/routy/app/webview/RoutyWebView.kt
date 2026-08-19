package com.routy.app.webview

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * One WebView instance per shell tab (see persistent stacking in RoutyShellScreen). Each tab
 * keeps its own view across revisits; switching tabs hides inactive views instead of disposing them.
 *
 * [onNavigateToLogin] is the sign-out signal: the web app redirects to /login whenever its
 * session cookie is missing or invalid, which covers every way that can happen — the token
 * expiring, a device being revoked from Settings, or the user tapping the web UI's own "sign
 * out" link inside this same WebView (which deletes the session behind the *shared* token,
 * cookie and bearer alike). Intercepting that navigation instead of rendering the web login page
 * keeps sign-in in one place — the native LoginScreen, with its TOTP handling — rather than
 * silently leaving the app pointed at a dead token.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RoutyWebView(
    url: String,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    restoredState: Bundle? = null,
    onStateSnapshot: (Bundle) -> Unit = {},
    onWebViewReady: (WebView) -> Unit = {},
) {
    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef.value?.let { webView ->
                val bundle = Bundle()
                webView.saveState(bundle)
                onStateSnapshot(bundle)
            }
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                        if (url != null && Uri.parse(url).path?.trimEnd('/')?.endsWith("/login") == true) {
                            onNavigateToLogin()
                        }
                    }
                }
                restoredState?.let { restoreState(it) } ?: loadUrl(url)
                webViewRef.value = this
                onWebViewReady(this)
            }
        },
        update = { webView ->
            webView.visibility = if (interactive) View.VISIBLE else View.GONE
            webView.isEnabled = interactive
            webView.setOnTouchListener(
                if (interactive) null else View.OnTouchListener { _, _ -> true },
            )
            if (webView.url?.substringBefore('?') != url.substringBefore('?')) {
                webView.loadUrl(url)
            }
        },
    )
}
