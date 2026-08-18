package com.routy.app

import android.app.Application
import com.routy.app.core.network.ApiClientProvider
import com.routy.app.core.storage.SecureStorage

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
    }
}
