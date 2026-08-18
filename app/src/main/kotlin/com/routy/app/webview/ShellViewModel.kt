package com.routy.app.webview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routy.app.core.network.ApiClientProvider
import com.routy.app.core.storage.SecureStorage
import com.routy.app.logic.api.SessionUser
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ShellUiState(
    val user: SessionUser? = null,
    /** True once /api/auth/me has confirmed the token or come back 401 — distinguishes "still checking" from "definitely signed out". */
    val checkedSession: Boolean = false,
    val signedOut: Boolean = false,
)

class ShellViewModel(
    private val secureStorage: SecureStorage,
    private val apiClientProvider: ApiClientProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ShellUiState())
    val uiState: StateFlow<ShellUiState> = _uiState.asStateFlow()

    init {
        refreshSession()
    }

    /** Also doubles as M1's "restore/validate the session on launch, route back to login on 401" check. */
    private fun refreshSession() {
        viewModelScope.launch {
            val response = try {
                apiClientProvider.service.me()
            } catch (_: IOException) {
                // Network hiccup, not necessarily an invalid session — stay on the shell rather
                // than bouncing to login just because the first request happened to fail.
                _uiState.value = _uiState.value.copy(checkedSession = true)
                return@launch
            }

            if (response.isSuccessful) {
                val body = response.body()
                _uiState.value = _uiState.value.copy(user = body?.user, checkedSession = true)
            } else if (response.code() == 401) {
                secureStorage.clearToken()
                apiClientProvider.invalidate()
                _uiState.value = _uiState.value.copy(checkedSession = true, signedOut = true)
            } else {
                _uiState.value = _uiState.value.copy(checkedSession = true)
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            try {
                apiClientProvider.service.logout()
            } catch (_: IOException) {
                // Best-effort — the token gets cleared locally regardless, which is what actually
                // matters for this device; the session just lingers server-side until it expires.
            }
            secureStorage.clearToken()
            apiClientProvider.invalidate()
            _uiState.value = _uiState.value.copy(signedOut = true)
        }
    }
}
