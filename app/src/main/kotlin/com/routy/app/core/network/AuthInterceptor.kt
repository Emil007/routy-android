package com.routy.app.core.network

import com.routy.app.core.storage.SecureStorage
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds `Authorization: Bearer <token>` to every request once logged in — mirrors how the server
 * accepts either a cookie or this header (src/lib/session.ts's getCurrentUser()), just always
 * using the header side of that since a native client has no cookie jar for the server's domain
 * outside of the WebView shell (which manages its own via CookieManager instead).
 */
class AuthInterceptor(private val secureStorage: SecureStorage) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = secureStorage.token
        val request = chain.request()
        return if (token != null) {
            chain.proceed(request.newBuilder().addHeader("Authorization", "Bearer $token").build())
        } else {
            chain.proceed(request)
        }
    }
}
