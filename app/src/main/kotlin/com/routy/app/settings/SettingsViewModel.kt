package com.routy.app.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routy.app.core.AccountLocale
import com.routy.app.core.AccountTheme
import com.routy.app.core.BootstrapLoader
import com.routy.app.core.BootstrapResult
import com.routy.app.core.network.ApiClientProvider
import com.routy.app.logic.api.ProfilePatchRequest
import com.routy.app.logic.api.SessionListEntry
import com.routy.app.logic.api.SessionUser
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val loading: Boolean = true,
    val user: SessionUser? = null,
    val sessions: List<SessionListEntry> = emptyList(),
    val saving: Boolean = false,
    val messageRes: Int? = null,
    val error: Boolean = false,
    val needsRecreate: Boolean = false,
)

class SettingsViewModel(
    private val apiClientProvider: ApiClientProvider,
    private val bootstrapLoader: BootstrapLoader,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = false, messageRes = null)
            try {
                val bootstrap = when (val result = bootstrapLoader.load()) {
                    is BootstrapResult.Fresh -> result.body.user
                    is BootstrapResult.NotModified -> result.cached.user
                    is BootstrapResult.CachedOnly -> result.cached.user
                    else -> null
                }
                val sessionsRes = apiClientProvider.service.sessions()
                val sessions = if (sessionsRes.isSuccessful) sessionsRes.body()?.sessions.orEmpty() else emptyList()
                _uiState.value = SettingsUiState(
                    loading = false,
                    user = bootstrap,
                    sessions = sessions,
                )
            } catch (_: IOException) {
                _uiState.value = _uiState.value.copy(loading = false, error = true)
            }
        }
    }

    fun setLocale(locale: String) {
        patchProfile(ProfilePatchRequest(locale = locale))
    }

    fun setTheme(theme: String) {
        patchProfile(ProfilePatchRequest(theme = theme))
    }

    fun revokeSession(sessionId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(saving = true, messageRes = null)
            try {
                val res = apiClientProvider.service.revokeSession(sessionId)
                if (res.isSuccessful) {
                    load()
                } else {
                    _uiState.value = _uiState.value.copy(saving = false, error = true)
                }
            } catch (_: IOException) {
                _uiState.value = _uiState.value.copy(saving = false, error = true)
            }
        }
    }

    fun revokeOtherSessions() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(saving = true, messageRes = null)
            try {
                val res = apiClientProvider.service.revokeOtherSessions()
                if (res.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        saving = false,
                        messageRes = com.routy.app.R.string.settings_revoked_others,
                    )
                    load()
                } else {
                    _uiState.value = _uiState.value.copy(saving = false, error = true)
                }
            } catch (_: IOException) {
                _uiState.value = _uiState.value.copy(saving = false, error = true)
            }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(messageRes = null, error = false)
    }

    fun consumeRecreate() {
        _uiState.value = _uiState.value.copy(needsRecreate = false)
    }

    private fun patchProfile(body: ProfilePatchRequest) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(saving = true, messageRes = null, error = false)
            try {
                val res = apiClientProvider.service.patchProfile(body)
                if (!res.isSuccessful) {
                    _uiState.value = _uiState.value.copy(saving = false, error = true)
                    return@launch
                }
                val user = res.body()?.user
                bootstrapLoader.invalidate()
                if (user != null) {
                    AccountLocale.apply(user.locale)
                    AccountTheme.apply(user.theme)
                    _uiState.value = _uiState.value.copy(
                        saving = false,
                        user = user,
                        needsRecreate = body.locale != null || body.theme != null,
                        messageRes = com.routy.app.R.string.settings_saved,
                    )
                } else {
                    _uiState.value = _uiState.value.copy(saving = false)
                    load()
                }
            } catch (_: IOException) {
                _uiState.value = _uiState.value.copy(saving = false, error = true)
            }
        }
    }
}
