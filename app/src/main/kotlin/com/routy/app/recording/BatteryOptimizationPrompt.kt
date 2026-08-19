package com.routy.app.recording

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.routy.app.R

/**
 * Several OEMs (Xiaomi/Samsung/Huawei/OnePlus, ...) layer their own aggressive background-kill
 * policies on top of stock Android's, which can kill even a proper foreground service within
 * minutes of the screen turning off unless the app is exempted from battery optimization.
 * ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS is restricted under Google Play policy for most
 * app categories, but this app is sideload-only by design (see the plan) — nothing stops using
 * it here, and it directly targets the one failure mode ("recording died in my pocket") the
 * whole native rewrite exists to avoid.
 */
@Composable
fun BatteryOptimizationPrompt(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val packageName = context.packageName
    val powerManager = remember { context.getSystemService<PowerManager>() }
    var dismissed by remember { mutableStateOf(false) }
    var ignoring by remember { mutableStateOf(powerManager?.isIgnoringBatteryOptimizations(packageName) ?: true) }

    // The system settings screen this opens doesn't hand back an ActivityResult callback we can
    // rely on either way (granted or backed out) — re-checking on every resume covers both.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                ignoring = powerManager?.isIgnoringBatteryOptimizations(packageName) ?: true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (ignoring || dismissed) return

    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(stringResource(R.string.battery_optimization_hint), modifier = Modifier.weight(1f))
            TextButton(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")),
                )
            }) {
                Text(stringResource(R.string.battery_optimization_allow))
            }
            TextButton(onClick = { dismissed = true }) {
                Text(stringResource(R.string.battery_optimization_dismiss))
            }
        }
    }
}
