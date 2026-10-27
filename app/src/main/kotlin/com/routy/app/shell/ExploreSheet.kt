package com.routy.app.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.routy.app.map.MapScreen

/**
 * Erkunden — Sheet-Stufe der Hülle. Hostet MapScreen im chromeHosted-Modus:
 * die Panels (Node/Segment/Draw/GPX/Edit/Split/Trash/Segments) bleiben hier,
 * die Karte liegt in MapChrome (eine Map, §7.4). ViewModel bleibt am
 * BackStackEntry der Shell — Werkzeug-Wechsel disposed nichts.
 */
@Composable
fun ExploreSheet(
    onStartRecording: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        MapScreen(
            onStartRecording = onStartRecording,
            modifier = Modifier.fillMaxSize(),
            chromeHosted = true,
        )
    }
}
