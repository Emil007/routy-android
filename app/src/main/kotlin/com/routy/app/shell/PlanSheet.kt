package com.routy.app.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.routy.app.route.RouteScreen

/**
 * Planen — Sheet-Stufe der Hülle. Hostet RouteScreen im chromeHosted-Modus:
 * Dock/Controls/Effekte (Planung, Accept, Follow, Tracking-Service) bleiben hier,
 * die Karte liegt in MapChrome (eine Map, §7.4).
 * In J2 liegt die aktive Route noch im Plan-Sheet (Gehen kommt in J3).
 */
@Composable
fun PlanSheet(
    onStartRecording: () -> Unit,
    accountLocaleTag: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        RouteScreen(
            onStartRecording = onStartRecording,
            accountLocaleTag = accountLocaleTag,
            modifier = Modifier.fillMaxSize(),
            chromeHosted = true,
        )
    }
}
