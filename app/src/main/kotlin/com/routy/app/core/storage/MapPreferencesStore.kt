package com.routy.app.core.storage

import android.content.Context
import com.routy.app.map.BaseMapStyle

/** Global map layer prefs (Settings → SharedPreferences), mirrored from web localStorage. */
class MapPreferencesStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var baseMapStyle: BaseMapStyle
        get() = runCatching { BaseMapStyle.valueOf(prefs.getString(KEY_STYLE, BaseMapStyle.STREETS.name)!!) }
            .getOrDefault(BaseMapStyle.STREETS)
        set(value) = prefs.edit().putString(KEY_STYLE, value.name).apply()

    var waymarkedOverlay: Boolean
        get() = prefs.getBoolean(KEY_WAYMARKED, false)
        set(value) = prefs.edit().putBoolean(KEY_WAYMARKED, value).apply()

    private companion object {
        const val PREFS_NAME = "routy_map_prefs"
        const val KEY_STYLE = "base_map_style"
        const val KEY_WAYMARKED = "waymarked_overlay"
    }
}
