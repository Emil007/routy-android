package com.routy.app

import android.app.Application
import com.routy.app.core.AccountLocale
import com.routy.app.core.network.ApiClientProvider
import com.routy.app.core.storage.SecureStorage
import com.routy.app.core.BootstrapLoader
import com.routy.app.core.storage.NetworkCache
import com.routy.app.core.GpxQueueNotifier
import com.routy.app.map.MapTileHttp
import com.routy.app.map.MapTilePrefetchScheduler
import org.maplibre.android.MapLibre

/** Application-scoped container — see ApiClientProvider's kdoc for why this is manual instead of a DI framework. */
class RoutyApplication : Application() {
    lateinit var secureStorage: SecureStorage
        private set
    lateinit var apiClientProvider: ApiClientProvider
        private set
    lateinit var routeProgressStore: com.routy.app.core.storage.RouteProgressStore
        private set
    lateinit var networkCache: com.routy.app.core.storage.NetworkCache
        private set
    lateinit var recordingSnapshotStore: com.routy.app.core.storage.RecordingSnapshotStore
        private set
    lateinit var recordingConfirmStore: com.routy.app.core.storage.RecordingConfirmStore
        private set
    lateinit var gpxCommitQueueStore: com.routy.app.core.storage.GpxCommitQueueStore
        private set
    lateinit var gpxCommitScheduler: com.routy.app.recording.GpxCommitScheduler
        private set
    lateinit var mapTilePrefetchScheduler: MapTilePrefetchScheduler
        private set
    lateinit var bootstrapLoader: BootstrapLoader
        private set

    override fun onCreate() {
        super.onCreate()
        secureStorage = SecureStorage(this)
        apiClientProvider = ApiClientProvider(secureStorage)
        routeProgressStore = com.routy.app.core.storage.RouteProgressStore(this)
        networkCache = com.routy.app.core.storage.NetworkCache(this)
        recordingSnapshotStore = com.routy.app.core.storage.RecordingSnapshotStore(this)
        recordingConfirmStore = com.routy.app.core.storage.RecordingConfirmStore(this)
        gpxCommitQueueStore = com.routy.app.core.storage.GpxCommitQueueStore(this)
        gpxCommitScheduler = com.routy.app.recording.GpxCommitScheduler(this, gpxCommitQueueStore)
        bootstrapLoader = BootstrapLoader(apiClientProvider, networkCache)
        mapTilePrefetchScheduler = MapTilePrefetchScheduler(this)
        networkCache.loadBootstrap()?.user?.locale?.let { AccountLocale.apply(it) }
        gpxCommitScheduler.schedulePending()
        GpxQueueNotifier.setPendingCount(gpxCommitQueueStore.listAll().size)
        MapTileHttp.install(this)
        // Must run once before any MapView is created (native Route screen, M3) — mirrors the
        // MapLibre.getInstance(this) call every getting-started guide puts in Activity.onCreate,
        // just hoisted here so it's guaranteed to happen exactly once regardless of which screen
        // first shows a map.
        MapLibre.getInstance(this)
    }
}
