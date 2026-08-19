package com.routy.app.core.network

import com.routy.app.logic.api.AdjustRouteRequest
import com.routy.app.logic.api.GenerateRouteRequest
import com.routy.app.logic.api.GenerateRouteResponse
import com.routy.app.logic.api.GpxCommitRequest
import com.routy.app.logic.api.GpxConfigResponse
import com.routy.app.logic.api.LoginRequest
import com.routy.app.logic.api.LoginResponse
import com.routy.app.logic.api.MeResponse
import com.routy.app.logic.api.NicknameRequest
import com.routy.app.logic.api.NodesResponse
import com.routy.app.logic.api.RouteStateResponse
import com.routy.app.logic.api.RouteTokenRequest
import com.routy.app.logic.api.SaveFavoriteRequest
import com.routy.app.logic.api.SegmentsResponse
import com.routy.app.logic.api.SessionsResponse
import com.routy.app.logic.api.ShareFavoriteRequest
import com.routy.app.logic.api.ShareFavoriteResponse
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * OkHttp requires a non-null body on every POST (unlike GET) — a @POST method with no @Body
 * parameter at all throws "method POST must have a request body" the moment it's actually
 * called, not at compile time. The server-side handlers for every endpoint below that has no
 * real payload (logout, complete, discard, favorite accept/delete) never read the request body
 * at all, so this placeholder just needs to exist, not mean anything.
 */
private val EMPTY_JSON_BODY: RequestBody = "{}".toRequestBody("application/json".toMediaType())

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
    suspend fun logout(@Body body: RequestBody = EMPTY_JSON_BODY): Response<Unit>

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

    @GET("api/route/state")
    suspend fun routeState(): Response<RouteStateResponse>

    @POST("api/route/generate")
    suspend fun generateRoute(@Body body: GenerateRouteRequest): Response<GenerateRouteResponse>

    @POST("api/route/widen")
    suspend fun widenRoute(@Body body: RouteTokenRequest): Response<GenerateRouteResponse>

    @POST("api/route/adjust")
    suspend fun adjustRoute(@Body body: AdjustRouteRequest): Response<GenerateRouteResponse>

    @POST("api/route/accept")
    suspend fun acceptRoute(@Body body: RouteTokenRequest): Response<Unit>

    @POST("api/route/nickname")
    suspend fun setRouteNickname(@Body body: NicknameRequest): Response<Unit>

    @POST("api/route/cancel")
    suspend fun cancelRoute(@Body body: RouteTokenRequest): Response<Unit>

    @POST("api/route/complete")
    suspend fun completeRoute(@Body body: RequestBody = EMPTY_JSON_BODY): Response<Unit>

    @POST("api/route/discard")
    suspend fun discardRoute(@Body body: RequestBody = EMPTY_JSON_BODY): Response<Unit>

    @POST("api/favorites")
    suspend fun saveFavorite(@Body body: SaveFavoriteRequest): Response<Unit>

    @POST("api/favorites/{id}/accept")
    suspend fun acceptFavorite(@Path("id") id: Int, @Body body: RequestBody = EMPTY_JSON_BODY): Response<Unit>

    @POST("api/favorites/{id}/delete")
    suspend fun deleteFavorite(@Path("id") id: Int, @Body body: RequestBody = EMPTY_JSON_BODY): Response<Unit>

    @POST("api/favorites/{id}/share")
    suspend fun shareFavorite(@Path("id") id: Int, @Body body: ShareFavoriteRequest): Response<ShareFavoriteResponse>

    @POST("api/gpx/commit")
    suspend fun commitGpx(@Body body: GpxCommitRequest): Response<Unit>

    @GET("api/gpx/config")
    suspend fun gpxConfig(): Response<GpxConfigResponse>
}
