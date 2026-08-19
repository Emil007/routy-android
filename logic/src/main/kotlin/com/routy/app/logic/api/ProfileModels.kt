package com.routy.app.logic.api

import kotlinx.serialization.Serializable

@Serializable
data class ProfilePatchRequest(
    val locale: String? = null,
    val theme: String? = null,
    val walkSpeedKmh: Double? = null,
)

@Serializable
data class ProfilePatchResponse(val user: SessionUser)

@Serializable
data class RevokeOthersResponse(val revoked: Int)
