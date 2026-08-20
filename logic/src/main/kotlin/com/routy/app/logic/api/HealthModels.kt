package com.routy.app.logic.api

import kotlinx.serialization.Serializable

@Serializable
data class CaptchaConfig(
    val provider: String = "none",
    val siteKey: String? = null,
    val scriptSrc: String? = null,
    val widgetClass: String? = null,
) {
    fun isRequired(): Boolean = provider != "none" && !siteKey.isNullOrBlank() && !scriptSrc.isNullOrBlank() && !widgetClass.isNullOrBlank()
}

/** Minimal public liveness — no setup/captcha fingerprinting. */
@Serializable
data class HealthResponse(
    val status: String,
    val version: String? = null,
    val versionDisplay: String? = null,
    val dbReachable: Boolean = false,
)

/** Login/onboarding bootstrap: setup flag + captcha widget config. */
@Serializable
data class PublicConfigResponse(
    val needsSetup: Boolean = false,
    val captcha: CaptchaConfig = CaptchaConfig(),
)

@Serializable
data class SetupRequest(
    val setupToken: String,
    val username: String,
    val password: String,
    val displayName: String? = null,
    val locale: String = "de",
    val deviceName: String? = null,
    val captchaToken: String? = null,
)
