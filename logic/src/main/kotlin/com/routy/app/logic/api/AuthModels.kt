package com.routy.app.logic.api

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
    val deviceName: String? = null,
    val totpCode: String? = null,
    val captchaToken: String? = null,
)

/** Mirrors SessionUser in src/lib/session.ts — the shape both /api/auth/login and /api/auth/me return. */
@Serializable
data class SessionUser(
    val id: Int,
    val username: String,
    val displayName: String,
    val locale: String,
    val walkSpeedKmh: Double? = null,
    val role: String,
    val active: Boolean,
    val theme: String,
    val totpEnabled: Boolean,
    val homeNodeId: Int? = null,
    val client: String = "web",
)

@Serializable
data class LoginResponse(val token: String, val user: SessionUser)

@Serializable
data class MeResponse(val user: SessionUser)

/** Every API error body is `{ "error": "<code>" }`, sometimes with extra fields (retryAfterSeconds, etc). */
@Serializable
data class ApiErrorBody(val error: String, val retryAfterSeconds: Int? = null)

@Serializable
data class SessionListEntry(
    val sessionId: String,
    val deviceName: String? = null,
    val client: String,
    val createdAt: String,
    val expiresAt: String,
    val isCurrent: Boolean,
)

@Serializable
data class SessionsResponse(val sessions: List<SessionListEntry>)
