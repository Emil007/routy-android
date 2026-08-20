package com.routy.app.core

import android.app.Application
import com.routy.app.BuildConfig
import io.sentry.Sentry
import io.sentry.android.core.SentryAndroid

/** Invoked reflectively from CrashReporting on release builds compiled with `-PsentryDsn`. */
@Suppress("unused")
object SentryBootstrap {
    @JvmStatic
    fun install(app: Application) {
        val dsn = BuildConfig.SENTRY_DSN.trim()
        if (dsn.isEmpty()) return
        SentryAndroid.init(app) { options ->
            options.dsn = dsn
            options.release = BuildConfig.VERSION_NAME
            options.environment = if (BuildConfig.DEBUG) "debug" else "release"
            options.isDebug = BuildConfig.DEBUG
        }
    }

    @JvmStatic
    fun close() {
        Sentry.close()
    }
}
