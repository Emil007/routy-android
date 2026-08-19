package com.routy.app.map

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.routy.app.logic.api.GeoPoint
import com.routy.app.logic.map.GeoBounds
import com.routy.app.logic.map.MapTileStyle
import com.routy.app.logic.map.boundsFromGeoPoints

class MapTilePrefetchScheduler(private val context: Context) {
    /** Prefetch route bbox for all map styles (best-effort, Wi‑Fi only). */
    fun prefetchRoute(
        geometry: List<GeoPoint>,
        styles: List<MapTileStyle> = MapTileStyle.entries.toList(),
    ) {
        val bounds = boundsFromGeoPoints(geometry) ?: return
        styles.forEach { schedule(bounds, it) }
    }

    private fun schedule(bounds: GeoBounds, style: MapTileStyle) {
        val work = OneTimeWorkRequestBuilder<MapTilePrefetchWorker>()
            .setInputData(
                workDataOf(
                    MapTilePrefetchWorker.KEY_STYLE to style.name,
                    MapTilePrefetchWorker.KEY_MIN_LAT to bounds.minLat,
                    MapTilePrefetchWorker.KEY_MIN_LNG to bounds.minLng,
                    MapTilePrefetchWorker.KEY_MAX_LAT to bounds.maxLat,
                    MapTilePrefetchWorker.KEY_MAX_LNG to bounds.maxLng,
                ),
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .build(),
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            MapTilePrefetchWorker.workName(bounds, style),
            ExistingWorkPolicy.KEEP,
            work,
        )
    }
}
