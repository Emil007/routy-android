package com.routy.app.ui.theme

import androidx.compose.ui.graphics.Color

// Routy's brand green (src/app/layout.tsx's viewport themeColor), plus a light/dark pair built
// around it — nothing fancier than what Material 3's tooling would generate from that one seed.
val RoutyGreen = Color(0xFF2E6B49)
val RoutyGreenLight = Color(0xFF4C8B67) // dark primary until J2 (docs/UNIFIED-REDESIGN.md J0)
val RoutyGreenDark = Color(0xFF1D4A32)
val RoutyGreenDarkAccent = Color(0xFF6FC08C) // J2 dark primary (web-parity), see Theme.kt
val RoutyGreenOnDark = Color(0xFF0E2418)

// Atlas chrome tokens (docs/UNIFIED-REDESIGN.md §6)
val ChromeCard = Color(0xFFFFFFF5) // light, 96 %
val ChromeCardDark = Color(0xF01B1F19) // dark, 94 %
val ChromeScrim = Color(0x521A1A1A) // 32 %
val ChromeScrimDark = Color(0x8F000000) // 56 %
val ChromeTopScrim = Color(0x66000000) // 40 % → fades

val RoutyBackground = Color(0xFFFAFAF7)
val RoutyBackgroundDark = Color(0xFF11140F)
val RoutySurface = Color(0xFFFFFFFF)
val RoutySurfaceDark = Color(0xFF1B1F19)
val RoutyError = Color(0xFFB3261E)
