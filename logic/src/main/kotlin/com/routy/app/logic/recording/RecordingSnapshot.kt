package com.routy.app.logic.recording

import kotlinx.serialization.Serializable

@Serializable
data class RecordingSnapshot(
    val phase: RecordingPhase,
    val startedAtMs: Long,
    val points: List<RecordingPoint>,
    val pausedPoints: List<RecordingPoint> = emptyList(),
    val totalPausedMs: Long = 0,
    val pausedAtMs: Long? = null,
)
