package com.routy.app.logic.recording

import kotlinx.serialization.Serializable

@Serializable
data class RecordingConfirmDraft(
    val points: List<RecordingPoint>,
    val startDecision: EndpointDecision,
    val endDecision: EndpointDecision,
    val markStartAsHome: Boolean = false,
)

fun RecordingConfirmDraft.matchesTrack(points: List<RecordingPoint>): Boolean {
    if (this.points.size != points.size || this.points.isEmpty()) return false
    val a = this.points.first()
    val b = points.first()
    val c = this.points.last()
    val d = points.last()
    return a.lat == b.lat && a.lng == b.lng && c.lat == d.lat && c.lng == d.lng
}
