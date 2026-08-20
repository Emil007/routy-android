package com.routy.app.webview

import android.webkit.CookieManager

/**
 * The web app authenticates via a `routy_session` cookie (src/lib/session.ts's SESSION_COOKIE);
 * the native login flow gets a bearer token instead — but they're the same underlying secret
 * (getCurrentUser() accepts either transport for the identical raw token), so handing the token
 * to CookieManager as that cookie's value makes every WebView-hosted page authenticate exactly
 * like a browser tab that just logged in, no server-side changes needed.
 */
object CookieBridge {
    private const val SESSION_COOKIE_NAME = "routy_session"

    fun installSessionCookie(baseUrl: String, token: String) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        // Secure + Path=/ + HttpOnly: CookieManager accepts the Set-Cookie attribute string.
        // No Max-Age: session cookie, cleared when clearSessionCookie() runs at sign-out.
        cookieManager.setCookie(
            baseUrl,
            "$SESSION_COOKIE_NAME=$token; Secure; Path=/; HttpOnly",
        )
        cookieManager.flush()
    }

    fun clearSessionCookie(baseUrl: String) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setCookie(baseUrl, "$SESSION_COOKIE_NAME=; Path=/; Max-Age=0")
        cookieManager.flush()
    }
}
