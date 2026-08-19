package com.routy.app.core.network

import com.routy.app.core.storage.SecureStorage
import com.routy.app.logic.api.ProfilePatchRequest
import com.routy.app.logic.api.ProfilePatchResponse
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
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
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            // Headers only, never BODY — request/response bodies can carry passwords and TOTP codes.
            level = HttpLoggingInterceptor.Level.HEADERS
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(secureStorage))
            .addInterceptor(RetryInterceptor())
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
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
