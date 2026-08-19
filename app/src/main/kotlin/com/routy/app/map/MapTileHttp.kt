package com.routy.app.map

import android.content.Context
import java.io.File
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.maplibre.android.module.http.HttpRequestUtil

/** Shared OkHttp disk cache for MapLibre tile requests and background prefetch. */
object MapTileHttp {
    private const val CACHE_BYTES = 80L * 1024 * 1024
    private var client: OkHttpClient? = null

    fun install(context: Context) {
        if (client != null) return
        val cache = Cache(File(context.cacheDir, "map_tiles"), CACHE_BYTES)
        val okHttp = OkHttpClient.Builder().cache(cache).build()
        client = okHttp
        HttpRequestUtil.setOkHttpClient(okHttp)
    }

    fun client(context: Context): OkHttpClient {
        install(context)
        return client!!
    }
}
