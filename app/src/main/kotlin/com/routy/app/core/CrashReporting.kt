package com.routy.app.core

import android.app.Application
import com.routy.app.BuildConfig
import com.routy.app.core.storage.CrashReportStore
import com.routy.app.core.storage.PendingCrashReport
import com.routy.app.core.storage.SecureStorage

/** Self-hosted crash capture (consent-gated); optional Sentry via release-only SentryBootstrap (reflection). */
object CrashReporting {
    fun install(app: Application, secureStorage: SecureStorage) {
        installSelfHostedCrashHandler(app, secureStorage)
        if (secureStorage.crashReportConsent) {
            tryInitSentry(app)
        }
    }

    fun enableSentry(app: Application) {
        tryInitSentry(app)
    }

    fun disableSentry() {
        runCatching {
            Class.forName("com.routy.app.core.SentryBootstrap")
                .getMethod("close")
                .invoke(null)
        }
    }

    private fun installSelfHostedCrashHandler(app: Application, secureStorage: SecureStorage) {
        val store = CrashReportStore(app)
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (secureStorage.crashReportConsent) {
                store.save(
                    PendingCrashReport(
                        message = throwable.message ?: throwable.javaClass.simpleName,
                        stack = throwable.stackTraceToString(),
                        appVersion = BuildConfig.VERSION_NAME,
                    ),
                )
            }
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
