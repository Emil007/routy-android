package com.routy.app.route

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Gehen-Fassade (J3): delegiert an RouteScreen im GO-Section-Modus für die
 * Sheet-Stufe. Zeigt nur die aktive Route (Folge, Voice, Track, Complete/Discard)
 * bzw. freies Gehen — nie Planungs-Formulare. Die Karte liegt in MapChrome.
 */
@Composable
fun GoScreen(
    onStartRecording: () -> Unit,
    accountLocaleTag: String,
    modifier: Modifier = Modifier,
    chromeHosted: Boolean = true,
) {
    RouteScreen(
        onStartRecording = onStartRecording,
        accountLocaleTag = accountLocaleTag,
        modifier = modifier,
        chromeHosted = chromeHosted,
        section = RouteSection.GO,
    )
}
