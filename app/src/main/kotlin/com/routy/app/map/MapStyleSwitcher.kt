package com.routy.app.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.routy.app.R

/** Base-layer picker plus optional Waymarked Trails raster overlay (mirrors web LayersControl). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapStyleSwitcher(
    selected: BaseMapStyle,
    onSelect: (BaseMapStyle) -> Unit,
    waymarkedOverlay: Boolean,
    onWaymarkedOverlayChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val waymarkedLabel = stringResource(R.string.map_style_waymarked)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(selected.labelRes()),
                    style = MaterialTheme.typography.labelMedium,
                )
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            }
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                BaseMapStyle.entries.forEach { style ->
                    DropdownMenuItem(
                        text = { Text(stringResource(style.labelRes())) },
                        onClick = {
                            onSelect(style)
                            expanded = false
                        },
                        trailingIcon = {
                            if (style == selected) {
                                Icon(Icons.Filled.Check, contentDescription = null)
                            }
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = waymarkedOverlay,
                onCheckedChange = onWaymarkedOverlayChange,
                modifier = Modifier.scale(0.75f),
            )
            Text(
                text = waymarkedLabel,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

private fun BaseMapStyle.labelRes(): Int = when (this) {
    BaseMapStyle.STREETS -> R.string.map_style_streets
    BaseMapStyle.HIKING -> R.string.map_style_hiking
    BaseMapStyle.SATELLITE -> R.string.map_style_satellite
}
