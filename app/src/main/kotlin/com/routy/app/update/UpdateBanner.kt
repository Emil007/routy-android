package com.routy.app.update

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.routy.app.BuildConfig
import com.routy.app.R
import com.routy.app.logic.update.isNewerVersion

/**
 * Sideload-only distribution (no Play Store) means no auto-update — mirrors the web's own
 * admin-only update notice (src/lib/updateCheck.ts, src/components/UpdateBanner.tsx) but checks
 * Emil007/routy-android's releases instead of Emil007/routy's, and shows to any user rather than
 * admin-only, since an out-of-date sideloaded APK is every user's problem, not just the sysop's.
 * Dismissal is session-only (a Compose remember, not persisted) — reappears next app launch if
 * still out of date, same as the web banner reappearing on next admin page load.
 */
@Composable
fun UpdateBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var updateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var dismissed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val info = GithubReleaseClient.fetchLatestRelease() ?: return@LaunchedEffect
        if (isNewerVersion(info.latestVersion, BuildConfig.VERSION_NAME)) {
            updateInfo = info
        }
    }

    val info = updateInfo
    if (info != null && !dismissed) {
        Card(modifier = modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(stringResource(R.string.update_available, info.latestVersion), modifier = Modifier.weight(1f))
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.url))) }) {
                    Text(stringResource(R.string.update_view))
                }
                IconButton(onClick = { dismissed = true }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.update_dismiss))
                }
            }
        }
    }
}
