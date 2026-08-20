package com.routy.app.logic.api

import kotlinx.serialization.Serializable

@Serializable
data class RestrictSegmentRequest(
    val segmentId: Int,
    /** "personal" | "global" */
    val scope: String,
    val reason: String? = null,
    val days: Int = 7,
    val clear: Boolean = false,
)

@Serializable
data class RestrictSegmentResponse(
    val ok: Boolean = true,
    val action: String? = null,
    val lockedUntil: String? = null,
    val proposalId: Int? = null,
)
