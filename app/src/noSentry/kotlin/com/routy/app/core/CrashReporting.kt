package com.routy.app.core

import android.app.Application
import com.routy.app.BuildConfig
import com.routy.app.core.storage.CrashReportStore
import com.routy.app.core.storage.PendingCrashReport

/** No-op when the app is built without `-PsentryDsn` (CI debug, local dev). Saves crash to disk for self-hosted upload. */
object CrashReporting {
    fun install(app: Application) {
        val store = CrashReportStore(app)
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            store.save(
                PendingCrashReport(
                    message = throwable.message ?: throwable.javaClass.simpleName,
                    stack = throwable.stackTraceToString(),
                    appVersion = BuildConfig.VERSION_NAME,
                ),
            )
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
