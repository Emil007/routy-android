package com.routy.app.core.storage

import android.content.Context
import com.routy.app.logic.api.NodeDto
import com.routy.app.logic.api.SegmentDto
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Local cache for nodes/segments with ETag for offline/resilient startup. */
class NetworkCache(context: Context) {
    private val dir = File(context.filesDir, "network_cache").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class CachedNetwork(
        val etag: String,
        val nodes: List<NodeDto>,
        val segments: List<SegmentDto>,
        val cachedAtMs: Long,
    )

    fun save(etag: String, nodes: List<NodeDto>, segments: List<SegmentDto>) {
        val payload = CachedNetwork(etag, nodes, segments, System.currentTimeMillis())
        File(dir, "network.json").writeText(json.encodeToString(payload))
    }

    fun load(): CachedNetwork? {
        val file = File(dir, "network.json")
        if (!file.exists()) return null
        return runCatching { json.decodeFromString<CachedNetwork>(file.readText()) }.getOrNull()
    }
}
