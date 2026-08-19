package com.routy.app.recording

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.routy.app.R

@Composable
fun RecordingActiveBanner(onOpenRecording: () -> Unit, modifier: Modifier = Modifier) {
    val active by RecordingForegroundService.recordingActive.collectAsState()
    if (!active) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenRecording),
    ) {
        Text(
            text = stringResource(R.string.shell_recording_active),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
