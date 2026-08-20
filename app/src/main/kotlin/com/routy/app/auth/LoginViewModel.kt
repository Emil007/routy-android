package com.routy.app.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routy.app.R
import com.routy.app.core.AccountLocale
import com.routy.app.core.AccountTheme
import com.routy.app.core.network.ApiClientProvider
import com.routy.app.core.storage.SecureStorage
import com.routy.app.logic.api.ApiErrorBody
import com.routy.app.logic.api.CaptchaConfig
import com.routy.app.logic.api.LoginRequest
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

data class LoginUiState(
    val loading: Boolean = false,
    val showTotpField: Boolean = false,
    val errorRes: Int? = null,
    val loggedIn: Boolean = false,
    val captcha: CaptchaConfig = CaptchaConfig(),
    val captchaRequired: Boolean = false,
    val captchaToken: String? = null,
)

class LoginViewModel(
    private val secureStorage: SecureStorage,
    private val apiClientProvider: ApiClientProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val errorJson = Json { ignoreUnknownKeys = true }

    init {
        loadHealth()
    }

    fun loadHealth() {
        viewModelScope.launch {
            // Health for connectivity; captcha comes from public-config (not health).
            runCatching { apiClientProvider.service.health() }
            val config = runCatching { apiClientProvider.service.getPublicConfig() }.getOrNull()
            val captcha = config?.takeIf { it.isSuccessful }?.body()?.captcha ?: CaptchaConfig()
            _uiState.value = _uiState.value.copy(
                captcha = captcha,
                captchaRequired = captcha.isRequired(),
            )
        }
    }

    fun setCaptchaToken(token: String?) {
        _uiState.value = _uiState.value.copy(captchaToken = token)
    }

    fun login(username: String, password: String, totpCode: String, deviceName: String) {
        val state = _uiState.value
        if (state.captchaRequired && state.captchaToken.isNullOrBlank()) {
            _uiState.value = state.copy(errorRes = R.string.login_captcha_required)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, errorRes = null)

            val response = try {
                apiClientProvider.service.login(
                    LoginRequest(
                        username = username,
                        password = password,
                        deviceName = deviceName.ifBlank { null },
                        totpCode = totpCode.ifBlank { null },
                        captchaToken = state.captchaToken,
                    ),
                )
            } catch (_: IOException) {
                _uiState.value = _uiState.value.copy(loading = false, errorRes = R.string.login_error_generic)
                return@launch
            }

            if (response.isSuccessful) {
                val body = response.body()
                if (body == null) {
                    _uiState.value = _uiState.value.copy(loading = false, errorRes = R.string.login_error_generic)
                    return@launch
                }
                secureStorage.token = body.token
                AccountLocale.apply(body.user.locale)
                AccountTheme.apply(body.user.theme)
                _uiState.value = _uiState.value.copy(loading = false, loggedIn = true)
                return@launch
            }

            val errorCode = parseErrorCode(response.errorBody()?.string())
            _uiState.value = when (errorCode) {
                "totp_required" -> _uiState.value.copy(loading = false, showTotpField = true, errorRes = null)
                "invalid_totp" -> _uiState.value.copy(loading = false, showTotpField = true, errorRes = R.string.login_error_totp)
                "invalid_credentials" -> _uiState.value.copy(loading = false, errorRes = R.string.login_error_invalid_credentials)
                "inactive" -> _uiState.value.copy(loading = false, errorRes = R.string.login_error_inactive)
                "locked" -> _uiState.value.copy(loading = false, errorRes = R.string.login_error_locked)
                "captcha_failed" -> _uiState.value.copy(loading = false, captchaToken = null, errorRes = R.string.login_captcha_error)
                else -> _uiState.value.copy(loading = false, errorRes = R.string.login_error_generic)
            }
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
}
