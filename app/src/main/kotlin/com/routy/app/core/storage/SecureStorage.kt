package com.routy.app.core.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Holds exactly two things: the server's base URL and the bearer token — both sensitive enough
 * (the token grants full account access; the URL identifies a private, unlisted server that
 * shouldn't leak into backups or a rooted device's plain-text app storage) to warrant
 * Keystore-backed encryption rather than plain SharedPreferences.
 */
class SecureStorage(context: Context) {
    private val prefs: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        prefs = EncryptedSharedPreferences.create(
            context,
            "routy_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    var serverUrl: String?
        get() = prefs.getString(KEY_SERVER_URL, null)
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value).apply()

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    var crashReportConsent: Boolean
        get() = prefs.getBoolean(KEY_CRASH_CONSENT, false)
        set(value) = prefs.edit().putBoolean(KEY_CRASH_CONSENT, value).apply()

    /** Clears the token (sign-out) but keeps the server URL — no need to re-onboard for a re-login. */
    fun clearToken() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_SERVER_URL = "server_url"
        const val KEY_TOKEN = "token"
        const val KEY_CRASH_CONSENT = "crash_report_consent"
    }
}
