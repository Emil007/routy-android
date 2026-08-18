package com.routy.app.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routy.app.R
import com.routy.app.core.network.ApiClientProvider
import com.routy.app.core.storage.SecureStorage
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface OnboardingUiState {
    data object Idle : OnboardingUiState
    data object Checking : OnboardingUiState
    data object Success : OnboardingUiState
    data class Error(val messageRes: Int) : OnboardingUiState
}

class OnboardingViewModel(
    private val secureStorage: SecureStorage,
    private val apiClientProvider: ApiClientProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow<OnboardingUiState>(OnboardingUiState.Idle)
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    /** Normalizes, stores, and probes a candidate server URL via GET /api/health before committing to it. */
    fun checkAndSaveServerUrl(rawUrl: String) {
        val normalized = normalizeUrl(rawUrl)
        if (normalized == null) {
            _uiState.value = OnboardingUiState.Error(R.string.onboarding_error_invalid_url)
            return
        }

        viewModelScope.launch {
            _uiState.value = OnboardingUiState.Checking
            // Set before probing (not after success) because ApiClientProvider reads the URL
            // from storage when building the client — the probe itself needs it there first.
            secureStorage.serverUrl = normalized
            apiClientProvider.invalidate()

            val reachable = try {
                apiClientProvider.service.health().isSuccessful
            } catch (_: IOException) {
                false
            }

            if (reachable) {
                _uiState.value = OnboardingUiState.Success
            } else {
                secureStorage.serverUrl = null
                apiClientProvider.invalidate()
                _uiState.value = OnboardingUiState.Error(R.string.onboarding_error_unreachable)
            }
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
