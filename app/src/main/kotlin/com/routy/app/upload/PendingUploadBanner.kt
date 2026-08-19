package com.routy.app.upload

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
import com.routy.app.core.GpxQueueNotifier

@Composable
fun PendingUploadBanner(modifier: Modifier = Modifier) {
    val pendingCount by GpxQueueNotifier.pendingCount.collectAsState()
    if (pendingCount <= 0) return

    Card(modifier = modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.shell_pending_uploads, pendingCount),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
