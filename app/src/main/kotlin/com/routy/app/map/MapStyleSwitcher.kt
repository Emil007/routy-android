package com.routy.app.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.routy.app.R

/** Base-layer picker mirroring the web's LayersControl (MapView.tsx's TILE_LAYERS) — same three tile sources, same default (streets first). */
@Composable
fun MapStyleSwitcher(selected: BaseMapStyle, onSelect: (BaseMapStyle) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BaseMapStyle.entries.forEach { style ->
            FilterChip(
                selected = selected == style,
                onClick = { onSelect(style) },
                label = { Text(stringResource(style.labelRes())) },
            )
        }
    }
}

private fun BaseMapStyle.labelRes(): Int = when (this) {
    BaseMapStyle.STREETS -> R.string.map_style_streets
    BaseMapStyle.HIKING -> R.string.map_style_hiking
    BaseMapStyle.SATELLITE -> R.string.map_style_satellite
}
