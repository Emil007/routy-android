package com.routy.app.recording

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.routy.app.R
import com.routy.app.ui.theme.ChromeCard
import com.routy.app.ui.theme.ChromeCardDark
import androidx.compose.foundation.isSystemInDarkTheme

/**
 * Schwebende Pille: zeigt an, dass eine Aufnahme läuft, und führt zurück in die
 * Aufnahme (J4). Sichtbar über allen Modi außer RECORD selbst — die Aufnahme bleibt
 * ereignisband im Hintergrund, auch während man etwas anderes anschaut.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingPill(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = if (dark) ChromeCardDark else ChromeCard,
        shadowElevation = 6.dp,
        modifier = modifier.clickable(onClick = onOpen),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.FiberManualRecord,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
            Text(
                text = stringResource(R.string.shell_recording_active),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
