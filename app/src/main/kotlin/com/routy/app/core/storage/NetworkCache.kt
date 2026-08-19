package com.routy.app.core.storage

import android.content.Context
import com.routy.app.logic.api.AppBootstrapResponse
import com.routy.app.logic.api.NodeDto
import com.routy.app.logic.api.SegmentDto
import com.routy.app.logic.cache.CachedBootstrap
import com.routy.app.logic.cache.CachedNetwork
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Local cache for bootstrap payload, network data, and ETag for offline/resilient startup. */
class NetworkCache(context: Context) {
    private val dir = File(context.filesDir, "network_cache").apply { mkdirs() }
    private val networkFile = File(dir, "network.json")
    private val bootstrapFile = File(dir, "bootstrap.json")
    private val json = Json { ignoreUnknownKeys = true }

    fun save(etag: String, nodes: List<NodeDto>, segments: List<SegmentDto>) {
        val payload = CachedNetwork(etag, nodes, segments, System.currentTimeMillis())
        networkFile.writeText(json.encodeToString(payload))
    }

    fun saveBootstrap(etag: String, body: AppBootstrapResponse) {
        val payload = CachedBootstrap(
            etag = etag,
            user = body.user,
            networkVersion = body.networkVersion,
            nodes = body.nodes,
            segments = body.segments,
            routeState = body.routeState,
            avoidSegmentIds = body.avoidSegmentIds,
            segmentConditions = body.segmentConditions,
            cachedAtMs = System.currentTimeMillis(),
        )
        bootstrapFile.writeText(json.encodeToString(payload))
        save(body.networkVersion, body.nodes, body.segments)
    }

    fun load(): CachedNetwork? {
        if (!networkFile.exists()) return null
        return runCatching { json.decodeFromString<CachedNetwork>(networkFile.readText()) }.getOrNull()
    }

    fun loadBootstrap(): CachedBootstrap? {
        if (!bootstrapFile.exists()) return null
        return runCatching { json.decodeFromString<CachedBootstrap>(bootstrapFile.readText()) }.getOrNull()
    }
}
