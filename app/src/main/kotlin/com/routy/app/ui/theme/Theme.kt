package com.routy.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = RoutyGreen,
    onPrimary = RoutySurface,
    background = RoutyBackground,
    surface = RoutySurface,
    error = RoutyError,
)

private val DarkColors = darkColorScheme(
    primary = RoutyGreenLight,
    onPrimary = RoutyBackgroundDark,
    background = RoutyBackgroundDark,
    surface = RoutySurfaceDark,
    error = RoutyError,
)

@Composable
fun RoutyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color (Material You, wallpaper-derived) is nice-to-have but off by default —
    // Routy's own green is the brand, matching the web app and the PWA icon rather than
    // whatever the device wallpaper happens to be.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            val activity = context as? Activity ?: return@SideEffect
            WindowCompat.getInsetsController(activity.window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}
