package com.routy.app.webview

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * One shared WebView per shell tab set, reused across tab switches by just changing the loaded
 * URL rather than recreating the view. Simple on purpose: each tab switch reloads the page (no
 * per-tab scroll-position/state preservation) — a deliberate trade-off for getting a fully
 * working shell quickly, not the ceiling of what's possible here.
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
fun RoutyWebView(url: String, onNavigateToLogin: () -> Unit, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                // Same-origin only by design (a private family server, not a general browser) —
                // no need to handle external links specially, nothing on these pages links
                // off-server except the /login redirect this client cares about.
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                        if (url != null && Uri.parse(url).path?.trimEnd('/')?.endsWith("/login") == true) {
                            onNavigateToLogin()
                        }
                    }
                }
                loadUrl(url)
            }
        },
        update = { webView ->
            if (webView.url?.substringBefore('?') != url.substringBefore('?')) {
                webView.loadUrl(url)
            }
        },
    )
}
