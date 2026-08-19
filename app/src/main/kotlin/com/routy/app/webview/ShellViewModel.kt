package com.routy.app.webview

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routy.app.core.BootstrapLoader
import com.routy.app.core.BootstrapResult
import com.routy.app.core.network.ApiClientProvider
import com.routy.app.core.storage.NetworkCache
import com.routy.app.core.storage.SecureStorage
import com.routy.app.logic.api.SessionUser
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ShellUiState(
    val user: SessionUser? = null,
    /** True once bootstrap/me has confirmed the token or come back 401 — distinguishes "still checking" from "definitely signed out". */
    val checkedSession: Boolean = false,
    val signedOut: Boolean = false,
)

class ShellViewModel(
    private val secureStorage: SecureStorage,
    private val apiClientProvider: ApiClientProvider,
    private val bootstrapLoader: BootstrapLoader,
    private val networkCache: NetworkCache,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ShellUiState())
    val uiState: StateFlow<ShellUiState> = _uiState.asStateFlow()

    init {
        networkCache.loadBootstrap()?.user?.let { applyUser(it) }
        refreshSession()
    }

    private fun applyUser(user: SessionUser) {
        user.locale.let { locale ->
            if (locale.isNotBlank()) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(locale))
            }
        }
        _uiState.value = _uiState.value.copy(user = user)
    }

    /** Bootstrap payload includes user profile — avoids a separate /api/auth/me round trip on launch. */
    private fun refreshSession() {
        viewModelScope.launch {
            when (val result = bootstrapLoader.load()) {
                is BootstrapResult.Fresh -> {
                    applyUser(result.body.user)
                    _uiState.value = _uiState.value.copy(checkedSession = true)
                }
                is BootstrapResult.NotModified -> {
                    applyUser(result.cached.user)
                    _uiState.value = _uiState.value.copy(checkedSession = true)
                }
                is BootstrapResult.CachedOnly -> {
                    applyUser(result.cached.user)
                    _uiState.value = _uiState.value.copy(checkedSession = true)
                }
                BootstrapResult.Unauthorized -> {
                    secureStorage.clearToken()
                    apiClientProvider.invalidate()
                    bootstrapLoader.invalidate()
                    _uiState.value = _uiState.value.copy(checkedSession = true, signedOut = true)
                }
                BootstrapResult.Failed -> {
                    _uiState.value = _uiState.value.copy(checkedSession = true)
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            try {
                apiClientProvider.service.logout()
            } catch (_: IOException) {
                // Best-effort — local token clear is what matters for this device.
            }
            bootstrapLoader.invalidate()
            secureStorage.clearToken()
            apiClientProvider.invalidate()
            _uiState.value = _uiState.value.copy(signedOut = true)
        }
    }
}
