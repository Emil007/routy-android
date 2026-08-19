package com.routy.app.core

import android.app.Application

/** No-op when the app is built without `-PsentryDsn` (CI debug, local dev). */
object CrashReporting {
    fun install(@Suppress("UNUSED_PARAMETER") app: Application) = Unit
}
