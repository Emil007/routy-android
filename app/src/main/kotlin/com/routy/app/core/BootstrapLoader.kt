package com.routy.app.core

import com.routy.app.core.network.ApiClientProvider
import com.routy.app.core.storage.NetworkCache
import com.routy.app.logic.cache.CachedBootstrap
import com.routy.app.logic.api.AppBootstrapResponse
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed class BootstrapResult {
    data class Fresh(val body: AppBootstrapResponse, val etag: String) : BootstrapResult()
    data class NotModified(val cached: CachedBootstrap) : BootstrapResult()
    data class CachedOnly(val cached: CachedBootstrap) : BootstrapResult()
    data object Unauthorized : BootstrapResult()
    data object Failed : BootstrapResult()
}

/** Single bootstrap fetch shared by shell and route — avoids duplicate API calls on launch. */
class BootstrapLoader(
    private val apiClientProvider: ApiClientProvider,
    private val networkCache: NetworkCache,
) {
    private val mutex = Mutex()
    private var lastResult: BootstrapResult? = null

    suspend fun load(): BootstrapResult = mutex.withLock {
        // Only Fresh / NotModified are memoized — always revalidate after CachedOnly / Failed / Unauthorized.
        when (val memoized = lastResult) {
            is BootstrapResult.Fresh, is BootstrapResult.NotModified -> return memoized
            else -> Unit
        }

        val cached = networkCache.loadBootstrap()
        val response = runCatching { apiClientProvider.service.bootstrap(cached?.etag) }.getOrNull()

        val result = when {
            response == null -> cached?.let { BootstrapResult.CachedOnly(it) } ?: BootstrapResult.Failed
            response.code() == 401 -> BootstrapResult.Unauthorized
            response.code() == 304 && cached != null -> BootstrapResult.NotModified(cached)
            response.isSuccessful -> {
                val body = response.body()
                if (body == null) BootstrapResult.Failed
                else {
                    val etag = response.headers()["ETag"]?.trim('"') ?: body.networkVersion
                    networkCache.saveBootstrap(etag, body)
                    BootstrapResult.Fresh(body, etag)
                }
            }
            cached != null -> BootstrapResult.CachedOnly(cached)
            else -> BootstrapResult.Failed
        }

        lastResult = when (result) {
            is BootstrapResult.Fresh, is BootstrapResult.NotModified -> result
            else -> null
        }
        result
    }

    fun invalidate() {
        lastResult = null
    }
}
