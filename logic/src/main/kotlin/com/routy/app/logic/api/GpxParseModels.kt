package com.routy.app.logic.api

import com.routy.app.logic.recording.NodeCandidate
import kotlinx.serialization.Serializable

@Serializable
data class GpxParseTrackPreview(
    val index: Int,
    val name: String? = null,
    val points: List<GpxPoint>,
    val lengthM: Int,
    val durationMin: Int,
    val elevation: GpxElevation? = null,
    val startNameGuess: String? = null,
    val endNameGuess: String? = null,
    val startCandidates: List<NodeCandidate> = emptyList(),
    val endCandidates: List<NodeCandidate> = emptyList(),
)

@Serializable
data class GpxParseResponse(val tracks: List<GpxParseTrackPreview>)
