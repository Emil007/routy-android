package com.routy.app.update

import com.routy.app.logic.api.GithubReleaseDto
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Headers

private interface GithubReleaseApi {
    @Headers("Accept: application/vnd.github+json")
    @GET("repos/Emil007/routy-android/releases/latest")
    suspend fun latestRelease(): Response<GithubReleaseDto>
}

data class AppUpdateInfo(val latestVersion: String, val url: String)

/**
 * Mirrors src/lib/updateCheck.ts's fetchLatestRelease — same GitHub Releases API, just
 * Emil007/routy-android instead of Emil007/routy. No routy server involved at all (this hits
 * GitHub directly, unauthenticated — a public repo's public releases endpoint), so this is a
 * standalone client rather than going through ApiClientProvider/ApiService.
 */
object GithubReleaseClient {
    private val api: GithubReleaseApi by lazy {
        val json = Json { ignoreUnknownKeys = true }
        Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GithubReleaseApi::class.java)
    }

    /** Best-effort, like the web's own fetchLatestRelease — any failure (offline, rate-limited, malformed response) just means no banner shows. */
    suspend fun fetchLatestRelease(): AppUpdateInfo? {
        val response = runCatching { api.latestRelease() }.getOrNull() ?: return null
        if (!response.isSuccessful) return null
        val tag = response.body()?.tagName ?: return null
        return AppUpdateInfo(
            latestVersion = tag.removePrefix("v"),
            url = response.body()?.htmlUrl ?: "https://github.com/Emil007/routy-android/releases",
        )
    }
}
