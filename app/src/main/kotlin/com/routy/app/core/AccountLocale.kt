package com.routy.app.core

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** Applies or clears the per-account locale for native Compose UI via AppCompat. */
object AccountLocale {
    fun apply(localeTag: String?) {
        if (localeTag.isNullOrBlank()) return
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(localeTag))
    }

    fun clear() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
    }
}
