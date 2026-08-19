package com.routy.app.logic.recording

/**
 * JSONL append log may contain points not yet flushed into [RecordingSnapshot] if the process
 * died between [appendPoint] and [saveSnapshot]. Returns only the tail not already in the snapshot.
 */
fun appendedPointsAfterSnapshot(snapshot: RecordingSnapshot, appended: List<RecordingPoint>): List<RecordingPoint> {
    val knownCount = snapshot.points.size + snapshot.pausedPoints.size
    return if (appended.size <= knownCount) emptyList() else appended.drop(knownCount)
}
