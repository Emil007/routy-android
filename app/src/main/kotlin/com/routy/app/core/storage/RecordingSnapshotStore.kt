package com.routy.app.core.storage

import android.content.Context
import com.routy.app.logic.recording.RecordingPoint
import com.routy.app.logic.recording.RecordingSnapshot
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Append-only GPS point log + phase snapshot for recording session recovery. */
class RecordingSnapshotStore(context: Context) {
    private val dir = File(context.filesDir, "recording").apply { mkdirs() }
    private val snapshotFile = File(dir, "snapshot.json")
    private val pointsFile = File(dir, "points.jsonl")
    private val json = Json { ignoreUnknownKeys = true }

    fun appendPoint(point: RecordingPoint) {
        pointsFile.appendText(json.encodeToString(point) + "\n")
    }

    fun saveSnapshot(snapshot: RecordingSnapshot) {
        snapshotFile.writeText(json.encodeToString(snapshot))
    }

    fun loadSnapshot(): RecordingSnapshot? {
        if (!snapshotFile.exists()) return null
        return runCatching { json.decodeFromString<RecordingSnapshot>(snapshotFile.readText()) }.getOrNull()
    }

    fun loadAppendedPoints(): List<RecordingPoint> {
        if (!pointsFile.exists()) return emptyList()
        return pointsFile.readLines().mapNotNull { line ->
            runCatching { json.decodeFromString<RecordingPoint>(line) }.getOrNull()
        }
    }

    fun clear() {
        snapshotFile.delete()
        pointsFile.delete()
    }
}
