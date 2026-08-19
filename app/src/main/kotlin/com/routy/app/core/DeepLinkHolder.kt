package com.routy.app.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One-shot deep link payloads consumed by RouteViewModel on launch. */
object DeepLinkHolder {
    private val _shareToken = MutableStateFlow<String?>(null)
    val shareToken: StateFlow<String?> = _shareToken.asStateFlow()

    fun setShareToken(token: String) {
        _shareToken.value = token
    }

    fun consumeShareToken(): String? {
        val token = _shareToken.value
        _shareToken.value = null
        return token
    }
}
