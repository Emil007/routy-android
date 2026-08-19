package com.routy.app.logic.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** GitHub's Releases API response shape — used by :app's update checker against routy-android's own releases, not the routy server. */
@Serializable
data class GithubReleaseDto(
    @SerialName("tag_name") val tagName: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
)
