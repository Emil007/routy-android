package com.routy.app.core

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** Applies or clears the per-account locale for native Compose UI via AppCompat. */
object AccountLocale {
    @Volatile
    private var lastTag: String? = null

    fun apply(localeTag: String?) {
        if (localeTag.isNullOrBlank()) return
        lastTag = localeTag
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(localeTag))
    }

    /** M8: last applied account locale tag — used for locating free-roam fallback strings. */
    fun currentTag(): String = lastTag ?: ""

    fun clear() {
        lastTag = null
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
    }
}
