package com.routy.app

import android.app.Application
import com.routy.app.core.network.ApiClientProvider
import com.routy.app.core.storage.SecureStorage
import org.maplibre.android.MapLibre

/** Application-scoped container — see ApiClientProvider's kdoc for why this is manual instead of a DI framework. */
class RoutyApplication : Application() {
    lateinit var secureStorage: SecureStorage
        private set
    lateinit var apiClientProvider: ApiClientProvider
        private set

    override fun onCreate() {
        super.onCreate()
        secureStorage = SecureStorage(this)
        apiClientProvider = ApiClientProvider(secureStorage)
        // Must run once before any MapView is created (native Route screen, M3) — mirrors the
        // MapLibre.getInstance(this) call every getting-started guide puts in Activity.onCreate,
        // just hoisted here so it's guaranteed to happen exactly once regardless of which screen
        // first shows a map.
        MapLibre.getInstance(this)
    }
}
