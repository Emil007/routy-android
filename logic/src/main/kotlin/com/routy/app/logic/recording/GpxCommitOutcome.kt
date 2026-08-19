package com.routy.app.logic.recording

enum class GpxCommitOutcome {
    Success,
    Retry,
    PermanentFailure,
}

fun gpxCommitOutcome(isSuccessful: Boolean, httpCode: Int): GpxCommitOutcome = when {
    isSuccessful -> GpxCommitOutcome.Success
    httpCode in 500..599 || httpCode == 429 -> GpxCommitOutcome.Retry
    else -> GpxCommitOutcome.PermanentFailure
}
