package com.routy.app.core.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Retries idempotent GET requests on transient network/server failures. */
class RetryInterceptor(
    private val maxAttempts: Int = 3,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method != "GET") return chain.proceed(request)

        var lastException: IOException? = null
        var response: Response? = null
        var retryAfterSeconds: Int? = null

        repeat(maxAttempts) { attempt ->
            response?.close()
            response = null

            try {
                response = chain.proceed(request)
                if (response!!.isSuccessful || !response!!.code.isRetryable) {
                    return response!!
                }
                // Respect the server's "Retry-After" for 429s (rounded up, capped).
                retryAfterSeconds = if (response!!.code == 429) {
                    response!!.header("Retry-After")?.toIntOrNull()?.coerceIn(1, 60)
                } else {
                    null
                }
            } catch (e: IOException) {
                lastException = e
            }

            if (attempt < maxAttempts - 1) {
                sleepBackoff(attempt, retryAfterSeconds)
            }
        }

        response?.let { return it }
        throw lastException ?: IOException("Request failed after $maxAttempts attempts")
    }

    private val Int.isRetryable: Boolean
        get() = this == 429 || this in 500..599

    private fun sleepBackoff(attempt: Int, retryAfterSeconds: Int?) {
        val delayMs = if (retryAfterSeconds != null) {
            retryAfterSeconds * 1000L
        } else {
            (500L shl attempt).coerceAtMost(4_000L)
        }.coerceAtMost(60_000L)
        try {
            TimeUnit.MILLISECONDS.sleep(delayMs)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}
