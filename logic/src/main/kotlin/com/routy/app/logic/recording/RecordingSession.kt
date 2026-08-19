package com.routy.app.logic.recording

import com.routy.app.logic.geo.ElevationStats
import com.routy.app.logic.geo.LatLng
import com.routy.app.logic.geo.elevationStats
import com.routy.app.logic.geo.estimateMinutes
import com.routy.app.logic.geo.haversineMeters
import com.routy.app.logic.geo.pathLengthMeters

enum class RecordingPhase { IDLE, RECORDING, PAUSED, CONFIRM }

data class RecordingPoint(val lat: Double, val lng: Double, val ele: Double? = null, val timestampMs: Long)

data class RecordingStats(val lengthM: Double, val durationMinutes: Int)

/**
 * Accumulates GPS points for one recording, mirroring RecordTrackWizard.tsx's
 * idle -> recording -> paused -> confirm phases. UI-framework-agnostic on purpose: the
 * foreground service just calls addPoint() on every location update regardless of what's
 * currently on screen, and the phase governs only whether the wizard is listening.
 */
class RecordingSession {
    var phase: RecordingPhase = RecordingPhase.IDLE
        private set

    private val _points = mutableListOf<RecordingPoint>()
    val points: List<RecordingPoint> get() = _points

    private var startedAtMs: Long? = null

    fun start(atMs: Long) {
        check(phase == RecordingPhase.IDLE) { "can only start from IDLE, was $phase" }
        phase = RecordingPhase.RECORDING
        startedAtMs = atMs
        _points.clear()
    }

    fun pause() {
        check(phase == RecordingPhase.RECORDING) { "can only pause while RECORDING, was $phase" }
        phase = RecordingPhase.PAUSED
    }

    fun resume() {
        check(phase == RecordingPhase.PAUSED) { "can only resume from PAUSED, was $phase" }
        phase = RecordingPhase.RECORDING
    }

    /** No-op outside RECORDING (e.g. a stray location callback arriving after pause/stop) rather than throwing — GPS updates race the UI by nature. */
    fun addPoint(point: RecordingPoint) {
        if (phase != RecordingPhase.RECORDING) return
        _points.add(point)
    }

    fun finish() {
        check(phase == RecordingPhase.RECORDING || phase == RecordingPhase.PAUSED) {
            "can only finish while RECORDING or PAUSED, was $phase"
        }
        phase = RecordingPhase.CONFIRM
    }

    fun discard() {
        phase = RecordingPhase.IDLE
        _points.clear()
        startedAtMs = null
    }

    fun stats(walkSpeedKmhFallback: Double): RecordingStats {
        val latLngs = _points.map { LatLng(it.lat, it.lng) }
        val lengthM = pathLengthMeters(latLngs)
        val started = startedAtMs
        val last = _points.lastOrNull()?.timestampMs
        val durationMinutes = if (started != null && last != null && last > started) {
            ((last - started) / 60000.0).let { if (it < 1) 1 else it.toInt() }
        } else {
            estimateMinutes(lengthM, walkSpeedKmhFallback)
        }
        return RecordingStats(lengthM, durationMinutes)
    }

    fun elevation(): ElevationStats? = elevationStats(_points.mapNotNull { it.ele })
}

/**
 * Mirrors RecordTrackWizard.tsx's GPS dedup (`haversineMeters(last, next) < 3`): a stationary or
 * slow-drifting fix shouldn't pad the track with near-identical points. Kept separate from
 * RecordingSession.addPoint() rather than folded into it, so the session's own tested contract
 * (it records whatever point it's given) stays simple — the foreground service is the one place
 * that decides whether a new fix is worth recording at all.
 */
fun shouldRecordPoint(lastPoint: RecordingPoint?, next: RecordingPoint, minDistanceM: Double = 3.0): Boolean {
    if (lastPoint == null) return true
    return haversineMeters(LatLng(lastPoint.lat, lastPoint.lng), LatLng(next.lat, next.lng)) >= minDistanceM
}
