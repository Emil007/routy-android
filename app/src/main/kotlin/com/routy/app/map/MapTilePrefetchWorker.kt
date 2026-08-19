package com.routy.app.map

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.routy.app.logic.map.GeoBounds
import com.routy.app.logic.map.MapTileStyle
import com.routy.app.logic.map.tileUrl
import com.routy.app.logic.map.tilesForBounds
import okhttp3.Request

/** Prefetches hiking raster tiles for a route bbox into the shared OkHttp disk cache. */
class MapTilePrefetchWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val style = runCatching {
            MapTileStyle.valueOf(inputData.getString(KEY_STYLE) ?: MapTileStyle.HIKING.name)
        }.getOrDefault(MapTileStyle.HIKING)
        val bounds = GeoBounds(
            minLat = inputData.getDouble(KEY_MIN_LAT, 0.0),
            minLng = inputData.getDouble(KEY_MIN_LNG, 0.0),
            maxLat = inputData.getDouble(KEY_MAX_LAT, 0.0),
            maxLng = inputData.getDouble(KEY_MAX_LNG, 0.0),
        )
        if (bounds.minLat == bounds.maxLat && bounds.minLng == bounds.maxLng) return Result.success()
        val tiles = tilesForBounds(bounds)
        if (tiles.isEmpty()) return Result.success()

        val http = MapTileHttp.client(applicationContext)
        for (tile in tiles) {
            val request = Request.Builder().url(tileUrl(style, tile.z, tile.x, tile.y)).build()
            try {
                http.newCall(request).execute().use { response ->
                    if (response.isSuccessful) response.body?.bytes()
                }
            } catch (_: Exception) {
                // Best-effort prefetch — partial cache still helps offline walks.
            }
        }
        return Result.success()
    }

    companion object {
        const val KEY_STYLE = "style"
        const val KEY_MIN_LAT = "min_lat"
        const val KEY_MIN_LNG = "min_lng"
        const val KEY_MAX_LAT = "max_lat"
        const val KEY_MAX_LNG = "max_lng"

        fun workName(bounds: GeoBounds) =
            "map_tile_prefetch_${bounds.minLat}_${bounds.minLng}_${bounds.maxLat}_${bounds.maxLng}"
    }
}
