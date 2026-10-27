package com.routy.app.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.routy.app.route.RouteScreen
import com.routy.app.route.RouteSection

/**
 * Planen — Sheet-Stufe der Hülle. Hostet Routen-Planung im chromeHosted-Modus:
 * Dock/Controls/Effekte (Planung, Accept) bleiben hier, die Karte liegt in
 * MapChrome (eine Map, §7.4). Seit J3 zeigt dieses Sheet NUR Planung (suggesting):
 * Annehmen kippt auf Gehen, die Live-Route liegt dann im GoSheet.
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
            section = RouteSection.PLAN,
        )
    }
}
