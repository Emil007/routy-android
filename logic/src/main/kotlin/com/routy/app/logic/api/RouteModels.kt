package com.routy.app.logic.api

import kotlinx.serialization.Serializable

@Serializable
data class RouteStation(
    val nodeId: Int,
    val name: String? = null,
    val lat: Double,
    val lng: Double,
    /** Full unabbreviated label for TTS — falls back to [name]. */
    val speakName: String? = null,
    /** Disambiguation segment when siblings connect the same two nodes. */
    val viaSegmentName: String? = null,
)

@Serializable
data class ShortStationGroup(val text: String, val viaSegmentName: String? = null)

@Serializable
data class RouteElevation(val gainM: Int, val lossM: Int)

/** Mirrors RouteDisplayPayload in src/components/RouteGenerator.tsx. */
@Serializable
data class RouteDisplayPayload(
    val nodeChain: List<Int>,
    val segmentIds: List<Int>,
    val lengthM: Int,
    val durationMin: Int,
    val stations: List<RouteStation>,
    val shortStationGroups: List<ShortStationGroup>,
    val elevation: RouteElevation? = null,
    val geometry: List<GeoPoint>,
)

@Serializable
data class GenerateRouteResponse(
    val token: String,
    val route: RouteDisplayPayload,
    val pointPreview: PointPreviewBreakdown? = null,
    val goldenHits: Int? = null,
    val goldenHitIds: List<Int> = emptyList(),
    val lengthRelaxed: Boolean = false,
    val lengthKm: Double? = null,
    val usingNetworkFallback: Boolean? = null,
    val guideMode: Boolean = false,
    val pointsMultiplier: Double? = null,
    val closedNodeWarnings: List<Int> = emptyList(),
)

@Serializable
data class GuideStartRequest(val orderedNodeIds: List<Int>, val loopBack: Boolean = true)

@Serializable
data class GenerateRouteRequest(
    val startNodeId: Int? = null,
    val destinationNodeId: Int? = null,
    val mustVisitNodeIds: List<Int> = emptyList(),
    val requiredSegmentIds: List<Int> = emptyList(),
    val excludedSegmentIds: List<Int> = emptyList(),
    val explorerMode: Boolean = false,
    /** "short" | "normal" | "long" | "surprise" */
    val preset: String? = null,
    val forceGolden: Boolean = false,
)

@Serializable
data class RouteTokenRequest(
    val token: String,
    /** M3: true replaces an already-active route (server answers 409 otherwise). */
    val replace: Boolean = false,
)

// --- Free-roam (0.47) ---

@Serializable
data class FreeRoamStartRequest(
    val lat: Double,
    val lng: Double,
    val startNodeId: Int? = null,
)

@Serializable
data class FreeRoamNeighbour(
    val nodeId: Int,
    val name: String? = null,
    val distanceM: Int = 0,
    val viaSegmentName: String? = null,
)

@Serializable
data class FreeRoamStartResponse(
    val token: String,
    val startNodeId: Int? = null,
    val startReachable: List<FreeRoamNeighbour> = emptyList(),
    val freeRoam: Boolean = true,
    val pointsMultiplier: Double? = null,
    val announcement: String? = null,
)

@Serializable
data class FreeRoamCheckinRequest(
    val token: String,
    val lat: Double,
    val lng: Double,
)

@Serializable
data class FreeRoamReachedNode(
    val nodeId: Int,
    val name: String? = null,
    val reachable: List<FreeRoamNeighbour> = emptyList(),
)

@Serializable
data class FreeRoamCheckinResponse(
    val ok: Boolean = true,
    val newlyReached: List<FreeRoamReachedNode> = emptyList(),
    val shouldAnnounce: Boolean = false,
    val announcement: String? = null,
    val visitedCount: Int = 0,
    // Stateless token rotation (decision K): the server re-signs on every
    // checkin; the client MUST store this fresh token and send it next.
    val token: String? = null,
)

@Serializable
data class FreeRoamCompleteRequest(
    val token: String,
    val trackPoints: List<com.routy.app.logic.route.RouteWalkTrackPoint> = emptyList(),
    val lengthM: Int,
    val durationMin: Int,
    val nickname: String? = null,
)

@Serializable
data class AdjustRouteRequest(val token: String, val direction: String) // "longer" | "shorter"

/** Server's Zod schema is `z.string().trim().max(120)`, not nullable — an empty string clears the nickname. */
@Serializable
data class NicknameRequest(val nickname: String)

@Serializable
data class FavoriteEntry(
    val id: Int,
    val name: String,
    val shareToken: String? = null,
    val display: RouteDisplayPayload,
)

@Serializable
data class FavoritesResponse(val favorites: List<FavoriteEntry>)

@Serializable
data class SaveFavoriteRequest(
    val name: String,
    val nodeChain: List<Int>,
    val segmentIds: List<Int>,
    val lengthM: Int,
    val durationMin: Int,
)

@Serializable
data class ShareFavoriteRequest(val enable: Boolean)

@Serializable
data class ShareFavoriteResponse(val ok: Boolean, val shareToken: String? = null)

@Serializable
data class CompleteRouteRequest(
    val trackPoints: List<com.routy.app.logic.route.RouteWalkTrackPoint> = emptyList(),
    /** M4: actual walked duration — server stores body value, not planned duration. */
    val durationMin: Int? = null,
)

/** POST /api/route/complete — walk delta points for the completion dialog. */
@Serializable
data class CompleteRouteResponse(
    val success: Boolean = true,
    val walkId: Int = 0,
    val pointsEarned: Int = 0,
    val streakMultiplier: Double = 1.0,
    val currentStreak: Int = 0,
    val pointBreakdown: PointPreviewBreakdown? = null,
    val goldenHits: Int = 0,
    /** "normal" | "golden" | "streak" | "achievement" */
    val celebrationTier: String = "normal",
    val guideMode: Boolean = false,
    val pointsMultiplier: Double? = null,
)

@Serializable
data class RateWalkRequest(val walkId: Int, val rating: Int)

/** GET /api/route/state — mirrors the props the web's /route RSC computes server-side (src/app/(app)/route/page.tsx). */
@Serializable
data class RouteStateResponse(
    val activeRoute: RouteDisplayPayload? = null,
    val nickname: String? = null,
    val favorites: List<FavoriteEntry> = emptyList(),
    /** "route" | "guide" */
    val walkMode: String = "route",
)
