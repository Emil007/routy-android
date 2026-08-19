package com.routy.app.logic.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
data class RouteStation(val nodeId: Int, val name: String? = null, val lat: Double, val lng: Double)

@Serializable
data class ShortStationGroup(val text: String, val viaSegmentName: String? = null)

@Serializable
data class RouteElevation(val gainM: Int, val lossM: Int)

/**
 * Unlike GeoPoint's default {lat, lng} object shape (used everywhere else, see NetworkModels.kt),
 * src/lib/routeDisplay.ts's RouteDisplay.geometry is a deliberate local exception encoded as
 * [lat, lng] tuple arrays (`geometry.push([p.lat, p.lng])`) — this serializer matches that one
 * field's actual wire format without touching GeoPoint's default serialization elsewhere.
 */
private object GeoPointTupleSerializer : KSerializer<GeoPoint> {
    private val delegate = ListSerializer(Double.serializer())
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: GeoPoint) {
        encoder.encodeSerializableValue(delegate, listOf(value.lat, value.lng))
    }

    override fun deserialize(decoder: Decoder): GeoPoint {
        val (lat, lng) = decoder.decodeSerializableValue(delegate)
        return GeoPoint(lat, lng)
    }
}

private object GeoPointTupleListSerializer : KSerializer<List<GeoPoint>> by ListSerializer(GeoPointTupleSerializer)

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
    @Serializable(with = GeoPointTupleListSerializer::class)
    val geometry: List<GeoPoint>,
)

@Serializable
data class GenerateRouteResponse(val token: String, val route: RouteDisplayPayload)

@Serializable
data class GenerateRouteRequest(
    val startNodeId: Int? = null,
    val destinationNodeId: Int? = null,
    val waypointNodeId: Int? = null,
    val explorerMode: Boolean = false,
    /** "short" | "long" — biases the search toward the lower/upper half of the configured length range. */
    val preset: String? = null,
)

@Serializable
data class RouteTokenRequest(val token: String)

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

/** GET /api/route/state — mirrors the props the web's /route RSC computes server-side (src/app/(app)/route/page.tsx). */
@Serializable
data class RouteStateResponse(
    val activeRoute: RouteDisplayPayload? = null,
    val nickname: String? = null,
    val favorites: List<FavoriteEntry> = emptyList(),
)
