package com.routy.app.core

import android.app.Application
import com.routy.app.BuildConfig
import com.routy.app.core.storage.CrashReportStore
import com.routy.app.core.storage.PendingCrashReport

/** Self-hosted crash capture always; optional Sentry via release-only SentryBootstrap (reflection). */
object CrashReporting {
    fun install(app: Application) {
        installSelfHostedCrashHandler(app)
        tryInitSentry(app)
    }

    private fun installSelfHostedCrashHandler(app: Application) {
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

    /** Release + `-PsentryDsn` only — class lives in `src/sentry`, not on debug classpath. */
    private fun tryInitSentry(app: Application) {
        if (BuildConfig.SENTRY_DSN.trim().isEmpty()) return
        runCatching {
            Class.forName("com.routy.app.core.SentryBootstrap")
                .getMethod("install", Application::class.java)
                .invoke(null, app)
        }
    }
}
