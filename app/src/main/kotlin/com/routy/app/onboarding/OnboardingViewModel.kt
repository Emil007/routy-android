package com.routy.app.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routy.app.R
import com.routy.app.core.AccountLocale
import com.routy.app.core.AccountTheme
import com.routy.app.core.network.ApiClientProvider
import com.routy.app.core.storage.SecureStorage
import com.routy.app.logic.api.ApiErrorBody
import com.routy.app.logic.api.CaptchaConfig
import com.routy.app.logic.api.SetupRequest
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

sealed interface OnboardingUiState {
    data object Idle : OnboardingUiState
    data object Checking : OnboardingUiState
    data object Success : OnboardingUiState
    data class NeedsSetup(val captcha: CaptchaConfig, val errorRes: Int? = null) : OnboardingUiState
    data object SetupComplete : OnboardingUiState
    data class Error(val messageRes: Int) : OnboardingUiState
}

class OnboardingViewModel(
    private val secureStorage: SecureStorage,
    private val apiClientProvider: ApiClientProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow<OnboardingUiState>(OnboardingUiState.Idle)
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val errorJson = Json { ignoreUnknownKeys = true }

    /** Normalizes, stores, and probes a candidate server URL via GET /api/health before committing to it. */
    fun checkAndSaveServerUrl(rawUrl: String) {
        val normalized = normalizeUrl(rawUrl)
        if (normalized == null) {
            _uiState.value = OnboardingUiState.Error(R.string.onboarding_error_invalid_url)
            return
        }

        viewModelScope.launch {
            _uiState.value = OnboardingUiState.Checking
            secureStorage.serverUrl = normalized
            apiClientProvider.invalidate()

            val healthResponse = try {
                apiClientProvider.service.health()
            } catch (_: IOException) {
                null
            }

            if (healthResponse?.isSuccessful != true) {
                secureStorage.serverUrl = null
                apiClientProvider.invalidate()
                _uiState.value = OnboardingUiState.Error(R.string.onboarding_error_unreachable)
                return@launch
            }

            val configResponse = try {
                apiClientProvider.service.getPublicConfig()
            } catch (_: IOException) {
                null
            }
            val config = configResponse?.takeIf { it.isSuccessful }?.body()
            if (config == null) {
                secureStorage.serverUrl = null
                apiClientProvider.invalidate()
                _uiState.value = OnboardingUiState.Error(R.string.onboarding_error_unreachable)
                return@launch
            }

            if (config.needsSetup) {
                _uiState.value = OnboardingUiState.NeedsSetup(config.captcha)
            } else {
                _uiState.value = OnboardingUiState.Success
            }
        }
    }

    fun submitSetup(
        setupToken: String,
        username: String,
        password: String,
        displayName: String,
        captcha: CaptchaConfig,
        captchaToken: String?,
        deviceName: String?,
    ) {
        if (setupToken.isBlank() || username.isBlank() || password.length < 6) {
            _uiState.value = OnboardingUiState.NeedsSetup(captcha, R.string.setup_error_invalid)
            return
        }
        if (captcha.isRequired() && captchaToken.isNullOrBlank()) {
            _uiState.value = OnboardingUiState.NeedsSetup(captcha, R.string.login_captcha_required)
            return
        }
        viewModelScope.launch {
            _uiState.value = OnboardingUiState.Checking
            val response = try {
                apiClientProvider.service.setup(
                    SetupRequest(
                        setupToken = setupToken.trim(),
                        username = username.trim(),
                        password = password,
                        displayName = displayName.trim().ifBlank { null },
                        locale = if (java.util.Locale.getDefault().language == "en") "en" else "de",
                        deviceName = deviceName,
                        captchaToken = captchaToken,
                    ),
                )
            } catch (_: IOException) {
                _uiState.value = OnboardingUiState.NeedsSetup(captcha, R.string.common_error)
                return@launch
            }
            if (response.isSuccessful) {
                val body = response.body()
                if (body == null) {
                    _uiState.value = OnboardingUiState.NeedsSetup(captcha, R.string.common_error)
                    return@launch
                }
                secureStorage.token = body.token
                AccountLocale.apply(body.user.locale)
                AccountTheme.apply(body.user.theme)
                _uiState.value = OnboardingUiState.SetupComplete
                return@launch
            }
            val errorCode = parseErrorCode(response.errorBody()?.string())
            val errorRes = when (errorCode) {
                "already_setup" -> R.string.setup_error_already_setup
                "invalid_setup_token" -> R.string.setup_error_invalid_token
                "captcha_failed" -> R.string.login_captcha_error
                "locked" -> R.string.login_error_locked
                else -> R.string.common_error
            }
            _uiState.value = OnboardingUiState.NeedsSetup(captcha, errorRes)
        }
    }

    private fun parseErrorCode(errorBodyJson: String?): String? {
        if (errorBodyJson == null) return null
        return try {
            errorJson.decodeFromString(ApiErrorBody.serializer(), errorBodyJson).error
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizeUrl(raw: String): String? {
        val trimmed = raw.trim().trimEnd('/')
        if (!trimmed.startsWith("https://")) return null
        return try {
            URI(trimmed)
            trimmed
        } catch (_: URISyntaxException) {
            null
        }
    }
}
