package com.routy.app.core.network

import com.routy.app.BuildConfig
import com.routy.app.core.storage.SecureStorage
import com.routy.app.logic.api.ProfilePatchRequest
import com.routy.app.logic.api.ProfilePatchResponse
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

private val json = Json {
    ignoreUnknownKeys = true // the server can add response fields later without breaking old app builds
    explicitNulls = false // omit null fields on the way out, so e.g. GpxEndpoint's unset half of its union never gets serialized
}

/**
 * Retrofit needs a fixed base URL at construction time, but the server address is user-supplied
 * at onboarding (and can change if they re-onboard against a different server) — so this holds a
 * lazily-(re)built ApiService instead of a static one, rebuilding only when the stored URL
 * actually changes. No DI framework: a single Application-scoped instance, manually constructed
 * in RoutyApplication, is simpler and has fewer moving parts to get wrong in an environment where
 * none of this can be compile-checked.
 */
class ApiClientProvider(private val secureStorage: SecureStorage) {
    private var cached: Pair<String, ApiService>? = null

    /** Throws IllegalStateException if onboarding hasn't set a server URL yet — callers should never reach this screen without one. */
    val service: ApiService
        get() {
            val baseUrl = normalizedBaseUrl(secureStorage.serverUrl)
            cached?.let { (cachedUrl, cachedService) -> if (cachedUrl == baseUrl) return cachedService }
            val service = buildApiService(baseUrl)
            cached = baseUrl to service
            return service
        }

    /** Called right after onboarding validates a new server URL, so the very next request already uses it. */
    fun invalidate() {
        cached = null
    }

    /** PATCH walk speed — sends explicit JSON null when clearing to network default. */
    suspend fun patchProfileWalkSpeed(kmh: Double?): Response<ProfilePatchResponse> {
        val mediaType = "application/json".toMediaType()
        val body = if (kmh == null) {
            """{"walkSpeedKmh":null}""".toRequestBody(mediaType)
        } else {
            json.encodeToString(ProfilePatchRequest(walkSpeedKmh = kmh)).toRequestBody(mediaType)
        }
        return service.patchProfile(body)
    }

    private fun buildApiService(baseUrl: String): ApiService {
        val clientBuilder = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(secureStorage))
            .addInterceptor(RetryInterceptor())
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)

        // Never attach header logging in release — Authorization/Cookie must not hit logcat.
        if (BuildConfig.DEBUG) {
            clientBuilder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.HEADERS
                },
            )
        }

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(clientBuilder.build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ApiService::class.java)
    }

    private fun normalizedBaseUrl(raw: String?): String {
        val url = checkNotNull(raw) { "Server URL not configured — onboarding should run before this is reached" }
        return if (url.endsWith("/")) url else "$url/"
    }
}

fun profilePatchBody(body: ProfilePatchRequest) =
    json.encodeToString(body).toRequestBody("application/json".toMediaType())

/** Unlock sends explicit JSON null for days — omitted nulls fail server Zod validation. */
fun segmentLockBody(segmentId: Int, days: Int?, reason: String?): RequestBody {
    val mediaType = "application/json".toMediaType()
    if (days != null) {
        return json.encodeToString(
            com.routy.app.logic.api.SegmentLockRequest(segmentId, days, reason),
        ).toRequestBody(mediaType)
    }
    val reasonJson = reason?.let { json.encodeToString(it) } ?: "null"
    return """{"segmentId":$segmentId,"days":null,"reason":$reasonJson}""".toRequestBody(mediaType)
}
