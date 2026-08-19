package com.routy.app.auth

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.routy.app.logic.api.CaptchaConfig

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CaptchaWebView(
    config: CaptchaConfig,
    baseUrl: String,
    onToken: (String) -> Unit,
    reloadKey: Int = 0,
    modifier: Modifier = Modifier,
) {
    if (!config.isRequired()) return

    val html = rememberCaptchaHtml(config)
    val origin = baseUrl.trim().trimEnd('/') + "/"

    key(reloadKey) {
        AndroidView(
            modifier = modifier.fillMaxWidth().height(120.dp),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun onCaptchaToken(token: String) {
                                post { onToken(token) }
                            }
                        },
                        "RoutyCaptcha",
                    )
                    webViewClient = WebViewClient()
                    loadDataWithBaseURL(origin, html, "text/html", "UTF-8", null)
                }
            },
        )
    }
}

private fun rememberCaptchaHtml(config: CaptchaConfig): String {
    val siteKey = config.siteKey.orEmpty()
    val scriptSrc = config.scriptSrc.orEmpty()
    val widgetClass = config.widgetClass.orEmpty()
    return """
        <!DOCTYPE html>
        <html><head>
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <script src="$scriptSrc" async defer></script>
        </head><body style="margin:0;padding:8px;">
        <div class="$widgetClass" data-sitekey="$siteKey" data-callback="onCaptcha"></div>
        <script>
        function onCaptcha(token) {
          if (window.RoutyCaptcha) RoutyCaptcha.onCaptchaToken(token);
        }
        </script>
        </body></html>
    """.trimIndent()
}
