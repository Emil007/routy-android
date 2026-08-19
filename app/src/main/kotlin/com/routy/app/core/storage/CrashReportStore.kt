package com.routy.app.core.storage

import android.content.Context
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class PendingCrashReport(
    val message: String,
    val stack: String? = null,
    val appVersion: String? = null,
    val savedAtMs: Long = System.currentTimeMillis(),
)

/** Persists uncaught exceptions locally for upload on next launch. */
class CrashReportStore(context: Context) {
    private val file = File(context.filesDir, "pending_crash.json")
    private val json = Json { ignoreUnknownKeys = true }

    fun save(report: PendingCrashReport) {
        file.writeText(json.encodeToString(report))
    }

    fun load(): PendingCrashReport? {
        if (!file.exists()) return null
        return runCatching { json.decodeFromString<PendingCrashReport>(file.readText()) }.getOrNull()
    }

    fun clear() {
        if (file.exists()) file.delete()
    }
}
