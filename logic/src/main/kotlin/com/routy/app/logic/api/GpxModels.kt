package com.routy.app.logic.api

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.serializer

@Serializable
data class GpxPoint(val lat: Double, val lng: Double, val ele: Double? = null)

@Serializable
data class GpxElevation(val gainM: Int, val lossM: Int, val minM: Int, val maxM: Int)

/**
 * POST /api/gpx/commit's `start`/`end` is a Zod union of `{ nodeId }` or `{ part1, part2 }` — an
 * existing junction vs. a brand-new one. Modeled here as one class with everything nullable;
 * the shared Json instance in :app is configured with explicitNulls = false, so whichever
 * fields are actually set are the only ones that reach the wire, naturally matching whichever
 * union branch applies without a custom serializer.
 */
@Serializable
data class GpxEndpoint(val nodeId: Int? = null, val part1: String? = null, val part2: String? = null) {
    companion object {
        fun existing(nodeId: Int) = GpxEndpoint(nodeId = nodeId)
        fun new(part1: String, part2: String) = GpxEndpoint(part1 = part1, part2 = part2)
    }
}

/**
 * M3: Elevation must ALWAYS reach the wire, as JSON `null` when none was recorded —
 * the server recomputes elevation itself and would 400 on a missing field. The app's
 * global Json uses `explicitNulls = false` (which also suppresses `@EncodeDefault`), so
 * this custom serializer emits the field explicitly while keeping every other field's
 * default-omission behaviour (GpxEndpoint union, GpxPoint.ele, markStartAsHome).
 */
@Serializable(with = GpxTrackSerializer::class)
data class GpxTrack(
    val points: List<GpxPoint>,
    val lengthM: Int,
    val durationMin: Int,
    val elevation: GpxElevation? = null,
    val start: GpxEndpoint,
    val end: GpxEndpoint,
    val markStartAsHome: Boolean? = null,
    val source: String = "gpx", // "gpx" | "drawn" — "gpx" for anything the app records itself.
)

@Serializable
data class GpxCommitRequest(val tracks: List<GpxTrack>)

/** GET /api/gpx/config — mirrors the settings.merge_radius_m / effectiveWalkSpeedKmh props the web's /map RSC computes server-side. */
@Serializable
data class GpxConfigResponse(val mergeRadiusM: Double, val walkSpeedKmh: Double)

@Serializable
data class PendingGpxCommit(
    val id: String,
    val enqueuedAtMs: Long,
    val request: GpxCommitRequest,
    /** Set when the server rejects the commit with a non-retryable status — kept for user review. */
    val permanentFailure: Boolean = false,
    val failureHttpCode: Int? = null,
    val failedAtMs: Long? = null,
)

/** Builds track JSON while omitting null defaults (matches the global formatter) — used for member fields. */
private val elementJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

@OptIn(ExperimentalSerializationApi::class)
object GpxTrackSerializer : KSerializer<GpxTrack> {
    private val pointsSerializer = ListSerializer(GpxPoint.serializer())

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("GpxTrack") {
        element<List<GpxPoint>>("points", isOptional = true)
        element<Int>("lengthM")
        element<Int>("durationMin")
        element<GpxElevation?>("elevation", isOptional = true)
        element<GpxEndpoint>("start")
        element<GpxEndpoint>("end")
        element<Boolean?>("markStartAsHome", isOptional = true)
        element<String>("source", isOptional = true)
    }

    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: GpxTrack) {
        val obj = buildJsonObject {
            put("points", elementJson.encodeToJsonElement(pointsSerializer, value.points))
            put("lengthM", JsonPrimitive(value.lengthM))
            put("durationMin", JsonPrimitive(value.durationMin))
            put(
                "elevation",
                value.elevation?.let { elementJson.encodeToJsonElement(GpxElevation.serializer(), it) }
                    ?: JsonNull,
            )
            put("start", elementJson.encodeToJsonElement(GpxEndpoint.serializer(), value.start))
            put("end", elementJson.encodeToJsonElement(GpxEndpoint.serializer(), value.end))
            value.markStartAsHome?.let { put("markStartAsHome", JsonPrimitive(it)) }
            put("source", JsonPrimitive(value.source))
        }
        encoder.encodeJsonElement(obj)
    }

    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): GpxTrack {
        val obj: JsonObject = decoder.decodeJsonElement() as JsonObject
        val points: List<GpxPoint> = obj["points"]
            ?.let { elementJson.decodeFromJsonElement(pointsSerializer, it) }
            .orEmpty()
        val elevationElem = obj["elevation"]
        val elevation = when (elevationElem) {
            is JsonNull, null -> null
            else -> elementJson.decodeFromJsonElement(GpxElevation.serializer(), elevationElem)
        }
        return GpxTrack(
            points = points,
            lengthM = obj["lengthM"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            durationMin = obj["durationMin"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            elevation = elevation,
            start = obj["start"]?.let { elementJson.decodeFromJsonElement(GpxEndpoint.serializer(), it) }
                ?: GpxEndpoint(),
            end = obj["end"]?.let { elementJson.decodeFromJsonElement(GpxEndpoint.serializer(), it) }
                ?: GpxEndpoint(),
            markStartAsHome = obj["markStartAsHome"]?.jsonPrimitive?.booleanOrNull,
            source = obj["source"]?.jsonPrimitive?.contentOrNull ?: "gpx",
        )
    }
}

private fun kotlinx.serialization.encoding.Encoder.encodeJsonElement(element: JsonElement) {
    encodeSerializableValue(JsonElement.serializer(), element)
}

private fun kotlinx.serialization.encoding.Decoder.decodeJsonElement(): JsonElement =
    decodeSerializableValue(JsonElement.serializer())
