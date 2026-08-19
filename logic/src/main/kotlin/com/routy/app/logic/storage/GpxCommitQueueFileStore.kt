package com.routy.app.logic.storage

import com.routy.app.logic.api.PendingGpxCommit
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** File-backed GPX commit queue — directory injectable for unit tests. */
class GpxCommitQueueFileStore(private val dir: File) {
    private val json = Json { ignoreUnknownKeys = true }

    init {
        dir.mkdirs()
    }

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
