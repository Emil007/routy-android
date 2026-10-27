package com.routy.app.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.routy.app.route.GoScreen

/**
 * Gehen — Sheet-Stufe der Hülle (J3). Hostet GoScreen im chromeHosted-Modus:
 * die aktive Route (Folge, Voice, Track, Complete/Discard) bzw. freies Gehen
 * (Reachable-Chips, Beenden) live — die Karte liegt in MapChrome (eine Map, §7.4).
 * Stay komponiert, damit RouteGenerator/ViewModels den Moduswechsel überleben.
 */
@Composable
fun GoSheet(
    onStartRecording: () -> Unit,
    accountLocaleTag: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        GoScreen(
            onStartRecording = onStartRecording,
            accountLocaleTag = accountLocaleTag,
            modifier = Modifier.fillMaxSize(),
            chromeHosted = true,
        )
    }
}
