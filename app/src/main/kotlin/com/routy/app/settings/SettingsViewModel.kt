package com.routy.app.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routy.app.core.AccountLocale
import com.routy.app.core.AccountTheme
import com.routy.app.core.BootstrapLoader
import com.routy.app.core.BootstrapResult
import com.routy.app.core.network.ApiClientProvider
import com.routy.app.core.network.profilePatchBody
import com.routy.app.logic.api.ProfilePatchRequest
import com.routy.app.logic.api.SessionListEntry
import com.routy.app.logic.api.SessionUser
import com.routy.app.logic.api.SegmentDto
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val loading: Boolean = true,
    val user: SessionUser? = null,
    val sessions: List<SessionListEntry> = emptyList(),
    val networkWalkSpeedKmh: Double = 5.0,
    val saving: Boolean = false,
    val messageRes: Int? = null,
    val error: Boolean = false,
    val needsRecreate: Boolean = false,
    val offlineCached: Boolean = false,
    val segments: List<SegmentDto> = emptyList(),
    val avoidSegmentIds: List<Int> = emptyList(),
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
            val bootstrapResult = bootstrapLoader.load()
            val offline = bootstrapResult is BootstrapResult.CachedOnly
            try {
                val (user, segments, avoidIds) = when (bootstrapResult) {
                    is BootstrapResult.Fresh -> Triple(bootstrapResult.body.user, bootstrapResult.body.segments, bootstrapResult.body.avoidSegmentIds)
                    is BootstrapResult.NotModified -> Triple(bootstrapResult.cached.user, bootstrapResult.cached.segments, bootstrapResult.cached.avoidSegmentIds)
                    is BootstrapResult.CachedOnly -> Triple(bootstrapResult.cached.user, bootstrapResult.cached.segments, bootstrapResult.cached.avoidSegmentIds)
                    else -> Triple(null, emptyList(), emptyList())
                }
                val sessionsRes = apiClientProvider.service.sessions()
                val sessions = if (sessionsRes.isSuccessful) sessionsRes.body()?.sessions.orEmpty() else emptyList()
                val configRes = runCatching { apiClientProvider.service.gpxConfig() }.getOrNull()
                val networkWalkSpeed = configRes?.takeIf { it.isSuccessful }?.body()?.walkSpeedKmh ?: 5.0
                _uiState.value = SettingsUiState(
                    loading = false,
                    offlineCached = offline,
                    user = user,
                    sessions = sessions,
                    networkWalkSpeedKmh = networkWalkSpeed,
                    segments = segments,
                    avoidSegmentIds = avoidIds,
                )
            } catch (_: IOException) {
                _uiState.value = SettingsUiState(
                    loading = false,
                    offlineCached = offline,
                    user = user,
                    sessions = emptyList(),
                    networkWalkSpeedKmh = _uiState.value.networkWalkSpeedKmh,
                    segments = segments,
                    avoidSegmentIds = avoidIds,
                    error = user == null,
                )
            }
        }
    }

    fun setLocale(locale: String) {
        patchProfile(ProfilePatchRequest(locale = locale))
    }

    fun setTheme(theme: String) {
        patchProfile(ProfilePatchRequest(theme = theme))
    }

    fun setWalkSpeed(kmh: Double?) {
        patchProfileWalkSpeed(kmh)
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

    fun addAvoidSegment(segmentId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(saving = true)
            try {
                val res = apiClientProvider.service.addAvoidSegment(com.routy.app.logic.api.AvoidSegmentRequest(segmentId))
                if (res.isSuccessful) {
                    bootstrapLoader.invalidate()
                    load()
                } else {
                    _uiState.value = _uiState.value.copy(saving = false, error = true)
                }
            } catch (_: IOException) {
                _uiState.value = _uiState.value.copy(saving = false, error = true)
            }
        }
    }

    fun removeAvoidSegment(segmentId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(saving = true)
            try {
                val res = apiClientProvider.service.removeAvoidSegment(com.routy.app.logic.api.AvoidSegmentRequest(segmentId))
                if (res.isSuccessful) {
                    bootstrapLoader.invalidate()
                    load()
                } else {
                    _uiState.value = _uiState.value.copy(saving = false, error = true)
                }
            } catch (_: IOException) {
                _uiState.value = _uiState.value.copy(saving = false, error = true)
            }
        }
    }

    fun consumeRecreate() {
        _uiState.value = _uiState.value.copy(needsRecreate = false)
    }

    private fun patchProfileWalkSpeed(kmh: Double?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(saving = true, messageRes = null, error = false)
            try {
                val res = apiClientProvider.patchProfileWalkSpeed(kmh)
                if (!res.isSuccessful) {
                    _uiState.value = _uiState.value.copy(saving = false, error = true)
                    return@launch
                }
                val user = res.body()?.user
                bootstrapLoader.invalidate()
                if (user != null) {
                    _uiState.value = _uiState.value.copy(
                        saving = false,
                        user = user,
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

    private fun patchProfile(body: ProfilePatchRequest) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(saving = true, messageRes = null, error = false)
            try {
                val res = apiClientProvider.service.patchProfile(profilePatchBody(body))
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
