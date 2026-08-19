package com.routy.app.widget

import android.content.Context
import androidx.core.content.edit

/** Cached streak/totals for the home-screen widget — written when stats refresh. */
object WidgetPrefs {
    private const val PREFS = "routy_widget"

    fun save(context: Context, currentStreak: Int, totalKm: Double) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putInt("current_streak", currentStreak)
            putFloat("total_km", totalKm.toFloat())
            putLong("updated_at", System.currentTimeMillis())
        }
    }

    fun currentStreak(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("current_streak", 0)

    fun totalKm(context: Context): Float =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getFloat("total_km", 0f)
}
