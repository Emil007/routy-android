package com.routy.app.logic.recording

import com.routy.app.logic.geo.ElevationStats
import com.routy.app.logic.geo.LatLng
import com.routy.app.logic.geo.elevationStats
import com.routy.app.logic.geo.estimateMinutes
import com.routy.app.logic.geo.pathLengthMeters

import kotlinx.serialization.Serializable

@Serializable
enum class RecordingPhase { IDLE, RECORDING, PAUSED, CONFIRM }

@Serializable
data class RecordingPoint(val lat: Double, val lng: Double, val ele: Double? = null, val timestampMs: Long)

data class RecordingStats(val lengthM: Double, val durationMinutes: Int)

/**
 * Accumulates GPS points for one recording. Paused fixes are buffered and merged on finish;
 * duration excludes accumulated pause time.
 */
class RecordingSession {
    var phase: RecordingPhase = RecordingPhase.IDLE
        private set

    private val _points = mutableListOf<RecordingPoint>()
    val points: List<RecordingPoint> get() = _points

    private val pausedPoints = mutableListOf<RecordingPoint>()
    private var startedAtMs: Long? = null
    private var pausedAtMs: Long? = null
    private var totalPausedMs: Long = 0

    fun start(atMs: Long) {
        check(phase == RecordingPhase.IDLE) { "can only start from IDLE, was $phase" }
        phase = RecordingPhase.RECORDING
        startedAtMs = atMs
        _points.clear()
        pausedPoints.clear()
        pausedAtMs = null
        totalPausedMs = 0
    }

    fun pause() {
        check(phase == RecordingPhase.RECORDING) { "can only pause while RECORDING, was $phase" }
        phase = RecordingPhase.PAUSED
        pausedAtMs = System.currentTimeMillis()
    }

    fun resume() {
        check(phase == RecordingPhase.PAUSED) { "can only resume from PAUSED, was $phase" }
        pausedAtMs?.let { totalPausedMs += System.currentTimeMillis() - it }
        pausedAtMs = null
        phase = RecordingPhase.RECORDING
    }

    fun addPoint(point: RecordingPoint) {
        when (phase) {
            RecordingPhase.RECORDING -> _points.add(point)
            RecordingPhase.PAUSED -> pausedPoints.add(point)
            else -> {}
        }
    }

    fun finish() {
        check(phase == RecordingPhase.RECORDING || phase == RecordingPhase.PAUSED) {
            "can only finish while RECORDING or PAUSED, was $phase"
        }
        if (phase == RecordingPhase.PAUSED) {
            pausedAtMs?.let { totalPausedMs += System.currentTimeMillis() - it }
            pausedAtMs = null
        }
        _points.addAll(pausedPoints)
        pausedPoints.clear()
        phase = RecordingPhase.CONFIRM
    }

    fun discard() {
        phase = RecordingPhase.IDLE
        _points.clear()
        pausedPoints.clear()
        startedAtMs = null
        pausedAtMs = null
        totalPausedMs = 0
    }

    fun snapshot(): RecordingSnapshot = RecordingSnapshot(
        phase = phase,
        startedAtMs = startedAtMs ?: 0L,
        points = _points.toList(),
        pausedPoints = pausedPoints.toList(),
        totalPausedMs = totalPausedMs,
        pausedAtMs = pausedAtMs,
    )

    fun restore(snapshot: RecordingSnapshot) {
        phase = snapshot.phase
        startedAtMs = snapshot.startedAtMs
        _points.clear()
        _points.addAll(snapshot.points)
        pausedPoints.clear()
        pausedPoints.addAll(snapshot.pausedPoints)
        totalPausedMs = snapshot.totalPausedMs
        pausedAtMs = snapshot.pausedAtMs
    }

    fun stats(walkSpeedKmhFallback: Double): RecordingStats {
        val allPoints = _points + if (phase == RecordingPhase.PAUSED) pausedPoints else emptyList()
        val latLngs = allPoints.map { LatLng(it.lat, it.lng) }
        val lengthM = pathLengthMeters(latLngs)
        val started = startedAtMs
        val last = allPoints.lastOrNull()?.timestampMs
        var pauseMs = totalPausedMs
        if (phase == RecordingPhase.PAUSED) {
            pausedAtMs?.let { pauseMs += System.currentTimeMillis() - it }
        }
        val durationMinutes = if (started != null && last != null && last > started) {
            val activeMs = (last - started - pauseMs).coerceAtLeast(0)
            val minutes = (activeMs / 60000.0).let { if (it < 1) 1 else it.toInt() }
            minutes
        } else {
            estimateMinutes(lengthM, walkSpeedKmhFallback)
        }
        return RecordingStats(lengthM, durationMinutes)
    }

    fun elevation(): ElevationStats? = elevationStats((_points + pausedPoints).mapNotNull { it.ele })
}

fun shouldRecordPoint(lastPoint: RecordingPoint?, next: RecordingPoint, minDistanceM: Double = 3.0): Boolean {
    if (lastPoint == null) return true
    return com.routy.app.logic.geo.haversineMeters(
        LatLng(lastPoint.lat, lastPoint.lng),
        LatLng(next.lat, next.lng),
    ) >= minDistanceM
}
