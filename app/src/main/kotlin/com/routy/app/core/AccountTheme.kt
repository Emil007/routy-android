package com.routy.app.core

/** Resolves the signed-in user's theme preference for native Compose (web-only themes fall back to system). */
object AccountTheme {
    private var preference: String? = null

    fun apply(theme: String?) {
        preference = theme?.takeIf { it.isNotBlank() }
    }

    fun clear() {
        preference = null
    }

    fun isDarkTheme(systemDark: Boolean): Boolean = when (preference) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }
}
