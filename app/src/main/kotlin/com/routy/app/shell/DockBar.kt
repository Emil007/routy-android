package com.routy.app.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.routy.app.R
import com.routy.app.ui.theme.ChromeCard
import com.routy.app.ui.theme.ChromeCardDark
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme

private data class DockChip(
    val mode: ShellMode,
    val label: Int,
    val icon: ImageVector,
    val disabled: Boolean,
    val comingNext: Boolean = false,
)

/**
 * Dock als Label-Chips (Erkunden · Planen · Gehen · Aufnahme) — Web-Parität (W4/W6).
 * Profil ist ein runder Knopf, kein 5. Chip. Bleibt bei laufender Tour sichtbar.
 * J3: Gehen aktiv; Annehmen bzw. Free-Roam-Start wechselt die Hülle automatisch auf
 * Gehen (MapChrome-LaunchedEffect), Aufnahme ruft die alte RecordingScreen.
 */
@Composable
fun DockBar(
    mode: ShellMode,
    onSelect: (ShellMode) -> Unit,
    onProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val chips = listOf(
        DockChip(ShellMode.PLAN, R.string.shell_mode_plan, Icons.Filled.Map, disabled = false),
        DockChip(ShellMode.EXPLORE, R.string.shell_mode_explore, Icons.Filled.Explore, disabled = false),
        DockChip(ShellMode.GO, R.string.shell_mode_go, Icons.AutoMirrored.Filled.DirectionsWalk, disabled = false),
    )

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = if (dark) ChromeCardDark else ChromeCard,
        shadowElevation = 6.dp,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            chips.forEach { chip ->
                val active = chip.mode == mode
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (active) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                    modifier = Modifier.clickable(enabled = !chip.disabled) { if (!chip.disabled) onSelect(chip.mode) },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = chip.icon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(chip.label),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            IconButton(onClick = onProfile, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Filled.AccountCircle,
                    contentDescription = stringResource(R.string.shell_profile),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
