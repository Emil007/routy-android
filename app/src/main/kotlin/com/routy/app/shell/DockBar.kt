package com.routy.app.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
 * J2: Gehen disabled (`shell_coming_next` als title); Aufnahme aktiv und ruft die
 * alte RecordingScreen (MapChrome übersetzt RECORD → onStartRecording).
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
        DockChip(ShellMode.EXPLORE, R.string.shell_mode_explore, Icons.Filled.Explore, disabled = false),
        DockChip(ShellMode.PLAN, R.string.shell_mode_plan, Icons.Filled.Map, disabled = false),
        DockChip(ShellMode.GO, R.string.shell_mode_go, Icons.AutoMirrored.Filled.DirectionsWalk, disabled = true, comingNext = true),
        DockChip(ShellMode.RECORD, R.string.shell_mode_record, Icons.Filled.FiberManualRecord, disabled = false),
    )

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = if (dark) ChromeCardDark else ChromeCard,
        shadowElevation = 6.dp,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            chips.forEach { chip ->
                val active = chip.mode == mode
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (active) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                    modifier = Modifier.clickable(enabled = !chip.disabled) { if (!chip.disabled) onSelect(chip.mode) },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = chip.icon,
                            contentDescription = null,
                            tint = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(chip.label),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onProfile) {
                Icon(
                    imageVector = Icons.Filled.AccountCircle,
                    contentDescription = stringResource(R.string.shell_profile),
                    modifier = Modifier.height(28.dp).background(androidx.compose.ui.graphics.Color.Transparent),
                )
            }
        }
    }
}
