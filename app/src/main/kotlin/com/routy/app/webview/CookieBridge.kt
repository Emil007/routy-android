package com.routy.app.webview

import android.content.Context
import android.webkit.CookieManager
import com.routy.app.RoutyApplication
import com.routy.app.core.network.ApiService

/**
 * M36/F52: the Android WebView must NEVER hold the long-lived API bearer in its
 * cookie jar. Instead the shell calls POST /api/auth/web-session with the bearer
 * (native Kotlin auth), gets a fresh short-lived (15 min) `routy_web` cookie and
 * hands only that to CookieManager. The server's Set-Cookie applies to the session
 * automatically on the next same-origin request; this object just forwards the
 * Set-Cookie header content. Old `routy_session` cookies from earlier installs are
 * cleared so an expired web session can never sign the whole app out.
 */
object CookieBridge {
    private const val SESSION_COOKIE_NAME = "routy_session"
    private const val WEB_COOKIE_NAME = "routy_web"

    /** Extracts a `routy_web=…; …` fragment from a Set-Cookie line, dropping `routy_session`.
     *  Review-Fix: der naive `split(",")` zerschnitt die Expires-Domäne (`Expires=Wed, 12 Nov …`),
     *  weil das Datum ein Komma enthält. Stattdessen: nur die Attribute bis zum ersten `;`
     *  behalten (`routy_web=<token>; Path=/; …`), die übrigen Attribute (HttpOnly/Secure/SameSite)
     *  setzt der Server ohnehin über die echte Set-Cookie des nächsten Requests. */
    fun cookieHeaderFromSetCookie(setCookie: String?): String? {
        if (setCookie.isNullOrBlank()) return null
        // Mehrere Set-Cookie-Blöcke können hintereinander stehen (trennen an `\n`);
        // innerhalb eines Blocks gibt es keine Kommata im Cookie-Namen/Wert.
        for (part in setCookie.split("\n")) {
            val trimmed = part.trim()
            if (!trimmed.startsWith("$WEB_COOKIE_NAME=")) continue
            val nameValueEnd = trimmed.indexOf(';')
            val core = if (nameValueEnd >= 0) trimmed.substring(0, nameValueEnd).trim() else trimmed
            if (core.startsWith("$WEB_COOKIE_NAME=")) return core
        }
        return null
    }

    fun installSessionCookie(baseUrl: String, cookie: String) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setCookie(baseUrl, cookie)
        cookieManager.flush()
    }

    fun clearLegacySessionCookie(baseUrl: String) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setCookie(baseUrl, "$SESSION_COOKIE_NAME=; Path=/; Max-Age=0")
        cookieManager.flush()
    }

    fun clearSessionCookie(baseUrl: String) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setCookie(baseUrl, "$WEB_COOKIE_NAME=; Path=/; Max-Age=0")
        cookieManager.setCookie(baseUrl, "$SESSION_COOKIE_NAME=; Path=/; Max-Age=0")
        cookieManager.flush()
    }
}

/** M36: echoes the Set-Cookie header value from POST /api/auth/web-session. */
class WebSessionBridge(private val context: Context) {
    private val service: ApiService
        get() = (context.applicationContext as RoutyApplication).apiClientProvider.service

    /** Returns the cookie header (`routy_web=…`) or null when the call failed. */
    suspend fun obtainWebCookie(): String? {
        val res = runCatching { service.createWebSession() }.getOrNull()
        if (res?.isSuccessful != true) return null
        val setCookie = res.headers()["Set-Cookie"]
        return CookieBridge.cookieHeaderFromSetCookie(setCookie)
    }
}
