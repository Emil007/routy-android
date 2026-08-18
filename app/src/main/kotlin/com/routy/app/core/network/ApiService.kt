package com.routy.app.core.network

import com.routy.app.logic.api.GenerateRouteRequest
import com.routy.app.logic.api.GenerateRouteResponse
import com.routy.app.logic.api.GpxCommitRequest
import com.routy.app.logic.api.LoginRequest
import com.routy.app.logic.api.LoginResponse
import com.routy.app.logic.api.MeResponse
import com.routy.app.logic.api.NodesResponse
import com.routy.app.logic.api.SegmentsResponse
import com.routy.app.logic.api.SessionsResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Only the endpoints the app actually calls, added as each milestone needs them rather than
 * transcribed wholesale up front — every one of these is traced against its actual route handler
 * in the server repo (src/app/api/...), not guessed. Uses Retrofit's Response<T> wrapper
 * everywhere instead of throwing on non-2xx, since every error path here is an expected,
 * UI-relevant outcome (wrong password, TOTP required, locked out) rather than a genuine
 * exception — the ApiErrorBody in the failure body is worth reading, not just discarding.
 */
interface ApiService {
    @GET("api/health")
    suspend fun health(): Response<Unit>

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): Response<LoginResponse>

    @POST("api/auth/logout")
    suspend fun logout(): Response<Unit>

    @GET("api/auth/me")
    suspend fun me(): Response<MeResponse>

    @GET("api/auth/sessions")
    suspend fun sessions(): Response<SessionsResponse>

    @DELETE("api/auth/sessions/{sessionId}")
    suspend fun revokeSession(@Path("sessionId") sessionId: String): Response<Unit>

    @GET("api/nodes")
    suspend fun nodes(): Response<NodesResponse>

    @GET("api/segments")
    suspend fun segments(): Response<SegmentsResponse>

    @POST("api/route/generate")
    suspend fun generateRoute(@Body body: GenerateRouteRequest): Response<GenerateRouteResponse>

    @POST("api/gpx/commit")
    suspend fun commitGpx(@Body body: GpxCommitRequest): Response<Unit>
}
