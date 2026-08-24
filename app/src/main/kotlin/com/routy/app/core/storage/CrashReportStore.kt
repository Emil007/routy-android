package com.routy.app.core.storage

import android.content.Context
import com.routy.app.logic.api.PendingCrashReport
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Persists uncaught exceptions locally for upload on next launch. */
class CrashReportStore(context: Context) {
    private val file = File(context.filesDir, "pending_crash.json")
    private val json = Json { ignoreUnknownKeys = true }

    fun save(report: PendingCrashReport) {
        file.writeText(json.encodeToString(PendingCrashReport.serializer(), report))
    }

    fun load(): PendingCrashReport? {
        if (!file.exists()) return null
        return runCatching { json.decodeFromString(PendingCrashReport.serializer(), file.readText()) }.getOrNull()
    }

    fun clear() {
        if (file.exists()) file.delete()
    }
}
