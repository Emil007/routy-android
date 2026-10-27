package com.routy.app.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.routy.app.R
import com.routy.app.map.MapStyleSwitcher
import com.routy.app.map.rememberMapPreferences
import com.routy.app.ui.theme.ChromeCard
import com.routy.app.ui.theme.ChromeCardDark

/**
 * Ebenen-Paneel (J5): Kartenstil + Waymarked-Overlay über derselben
 * MapPreferencesStore / rememberMapPreferences. Keine eigene Karte — der Träger
 * (FAB-Modal der Hülle oder die Einstellungs-Sektion) legt Rahmen und Abstand fest.
 */
@Composable
fun LayersSheet(modifier: Modifier = Modifier) {
    val mapPrefs = rememberMapPreferences()
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.settings_map_layers_title), style = MaterialTheme.typography.titleSmall)
        MapStyleSwitcher(
            selected = mapPrefs.baseMapStyle,
            onSelect = mapPrefs.setBaseMapStyle,
            waymarkedOverlay = mapPrefs.waymarkedOverlay,
            onWaymarkedOverlayChange = mapPrefs.setWaymarkedOverlay,
        )
    }
}

/** „Ebenen“-Pille als FAB-Ersatz (J5) — öffnet das LayersSheet als Modal über der Karte. */
@Composable
fun LayersFab(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = if (dark) ChromeCardDark else ChromeCard,
        shadowElevation = 6.dp,
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOpen,
            )
            .background(if (dark) ChromeCardDark else ChromeCard, RoundedCornerShape(28.dp)),
    ) {
        Text(
            text = stringResource(R.string.shell_layers),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}
