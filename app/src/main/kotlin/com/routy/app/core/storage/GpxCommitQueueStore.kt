package com.routy.app.core.storage

import android.content.Context
import com.routy.app.logic.api.GpxCommitRequest
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class PendingGpxCommit(
    val id: String,
    val enqueuedAtMs: Long,
    val request: GpxCommitRequest,
)

/** Durable queue for failed GPX commits — drained by [com.routy.app.recording.GpxCommitWorker]. */
class GpxCommitQueueStore(context: Context) {
    private val dir = File(context.filesDir, "gpx_commit_queue").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true }

    fun enqueue(pending: PendingGpxCommit) {
        File(dir, "${pending.id}.json").writeText(json.encodeToString(pending))
    }

    fun load(id: String): PendingGpxCommit? {
        val file = File(dir, "$id.json")
        if (!file.exists()) return null
        return runCatching { json.decodeFromString<PendingGpxCommit>(file.readText()) }.getOrNull()
    }

    fun listAll(): List<PendingGpxCommit> =
        dir.listFiles()?.mapNotNull { file ->
            runCatching { json.decodeFromString<PendingGpxCommit>(file.readText()) }.getOrNull()
        }.orEmpty().sortedBy { it.enqueuedAtMs }

    fun remove(id: String) {
        File(dir, "$id.json").delete()
    }
}
