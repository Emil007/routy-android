package com.routy.app.core.network

import com.routy.app.logic.api.AcceptProposalResponse
import com.routy.app.logic.api.AdjustRouteRequest
import com.routy.app.logic.api.AvoidListResponse
import com.routy.app.logic.api.AvoidSegmentRequest
import com.routy.app.logic.api.AppBootstrapResponse
import com.routy.app.logic.api.CrashReportRequest
import com.routy.app.logic.api.GenerateRouteRequest
import com.routy.app.logic.api.GenerateRouteResponse
import com.routy.app.logic.api.GpxCommitRequest
import com.routy.app.logic.api.GpxConfigResponse
import com.routy.app.logic.api.LoginRequest
import com.routy.app.logic.api.LoginResponse
import com.routy.app.logic.api.MeResponse
import com.routy.app.logic.api.NicknameRequest
import com.routy.app.logic.api.NodesResponse
import com.routy.app.logic.api.CompleteRouteResponse
import com.routy.app.logic.api.AppStatsMeResponse
import com.routy.app.logic.api.ProposalActionRequest
import com.routy.app.logic.api.ProposalsResponse
import com.routy.app.logic.api.ReportConditionRequest
import com.routy.app.logic.api.ReportConditionResponse
import com.routy.app.logic.api.RouteStateResponse
import com.routy.app.logic.api.RouteTokenRequest
import com.routy.app.logic.api.SaveFavoriteRequest
import com.routy.app.logic.api.SegmentsResponse
import com.routy.app.logic.api.SessionsResponse
import com.routy.app.logic.api.ShareFavoriteRequest
import com.routy.app.logic.api.ShareFavoriteResponse
import com.routy.app.logic.api.ShareRouteResponse
import com.routy.app.logic.api.WalkLogIdRequest
import com.routy.app.logic.api.WeeklyLeaderboardResponse
import com.routy.app.logic.api.PointsLeaderboardResponse
import com.routy.app.logic.api.ProfilePatchRequest
import com.routy.app.logic.api.ProfilePatchResponse
import com.routy.app.logic.api.GpxParseResponse
import com.routy.app.logic.api.NodeIdRequest
import com.routy.app.logic.api.NodeMoveRequest
import com.routy.app.logic.api.NodeRenameRequest
import com.routy.app.logic.api.SegmentGeometryRequest
import com.routy.app.logic.api.SegmentIdRequest
import com.routy.app.logic.api.SegmentLockRequest
import com.routy.app.logic.api.SegmentRenameRequest
import com.routy.app.logic.api.SegmentSplitRequest
import com.routy.app.logic.api.RevokeOthersResponse
import okhttp3.MultipartBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Multipart
import retrofit2.http.Part

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
    suspend fun nodes(@Header("If-None-Match") ifNoneMatch: String? = null): Response<NodesResponse>

    @GET("api/segments")
    suspend fun segments(@Header("If-None-Match") ifNoneMatch: String? = null): Response<SegmentsResponse>

    @GET("api/app/bootstrap")
    suspend fun bootstrap(@Header("If-None-Match") ifNoneMatch: String? = null): Response<AppBootstrapResponse>

    @GET("api/route/state")
    suspend fun routeState(): Response<RouteStateResponse>

    @GET("api/app/stats/me")
    suspend fun appStatsMe(): Response<AppStatsMeResponse>

    @GET("api/app/stats/leaderboard/weekly")
    suspend fun weeklyLeaderboard(): Response<WeeklyLeaderboardResponse>

    @GET("api/app/stats/leaderboard/points")
    suspend fun pointsLeaderboard(): Response<PointsLeaderboardResponse>

    @POST("api/app/stats/walks/delete")
    suspend fun deleteWalk(@Body body: WalkLogIdRequest): Response<Unit>

    @GET("api/share/{token}")
    suspend fun resolveShareToken(@Path("token") token: String): Response<ShareRouteResponse>

    @POST("api/share/{token}/accept")
    suspend fun acceptShareToken(@Path("token") token: String, @Body body: RequestBody = EMPTY_JSON_BODY): Response<Unit>

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
    suspend fun completeRoute(@Body body: RequestBody = EMPTY_JSON_BODY): Response<CompleteRouteResponse>

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

    @PATCH("api/app/profile")
    suspend fun patchProfile(@Body body: RequestBody): Response<ProfilePatchResponse>

    @GET("api/app/avoid")
    suspend fun avoidList(): Response<AvoidListResponse>

    @POST("api/app/avoid")
    suspend fun addAvoidSegment(@Body body: AvoidSegmentRequest): Response<AvoidListResponse>

    @HTTP(method = "DELETE", path = "api/app/avoid", hasBody = true)
    suspend fun removeAvoidSegment(@Body body: AvoidSegmentRequest): Response<AvoidListResponse>

    @POST("api/auth/sessions/revoke-others")
    suspend fun revokeOtherSessions(@Body body: RequestBody = EMPTY_JSON_BODY): Response<RevokeOthersResponse>

    @POST("api/nodes/rename")
    suspend fun renameNode(@Body body: NodeRenameRequest): Response<Unit>

    @POST("api/nodes/move")
    suspend fun moveNode(@Body body: NodeMoveRequest): Response<Unit>

    @POST("api/nodes/home")
    suspend fun setHomeNode(@Body body: NodeIdRequest): Response<Unit>

    @POST("api/nodes/delete")
    suspend fun deleteNode(@Body body: NodeIdRequest): Response<Unit>

    @POST("api/segments/rename")
    suspend fun renameSegment(@Body body: SegmentRenameRequest): Response<Unit>

    @POST("api/segments/lock")
    suspend fun lockSegment(@Body body: SegmentLockRequest): Response<Unit>

    @POST("api/segments/delete")
    suspend fun deleteSegment(@Body body: SegmentIdRequest): Response<Unit>

    @POST("api/segments/geometry")
    suspend fun updateSegmentGeometry(@Body body: SegmentGeometryRequest): Response<Unit>

    @POST("api/segments/split")
    suspend fun splitSegment(@Body body: SegmentSplitRequest): Response<Unit>

    @POST("api/segments/condition")
    suspend fun reportSegmentCondition(@Body body: ReportConditionRequest): Response<ReportConditionResponse>

    @GET("api/app/proposals")
    suspend fun proposals(): Response<ProposalsResponse>

    @POST("api/app/proposals/accept")
    suspend fun acceptProposal(@Body body: ProposalActionRequest): Response<AcceptProposalResponse>

    @POST("api/app/proposals/dismiss")
    suspend fun dismissProposal(@Body body: ProposalActionRequest): Response<Unit>

    @POST("api/app/crash")
    suspend fun reportCrash(@Body body: CrashReportRequest): Response<Unit>

    @Multipart
    @POST("api/gpx/parse")
    suspend fun parseGpx(@Part file: MultipartBody.Part): Response<GpxParseResponse>
}
