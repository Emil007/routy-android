package com.routy.app.widget

import android.content.Context
import com.routy.app.core.network.ApiService
import java.io.IOException

/** Persists stats snapshot and refreshes the home-screen widget. */
object WidgetUpdater {
    fun apply(context: Context, streak: Int, totalKm: Double) {
        WidgetPrefs.save(context, streak, totalKm)
        RoutyWidgetProvider.updateAll(context)
    }

    suspend fun refreshFromApi(context: Context, service: ApiService) {
        try {
            val body = service.appStatsMe().takeIf { it.isSuccessful }?.body() ?: return
            val streak = body.streak?.currentStreak ?: 0
            val km = (body.stats?.totalLengthM ?: 0) / 1000.0
            apply(context, streak, km)
        } catch (_: IOException) {
            // Offline — keep last cached widget values.
        }
    }
}
