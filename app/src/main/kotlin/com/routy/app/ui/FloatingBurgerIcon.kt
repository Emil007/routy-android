package com.routy.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Dunkle Striche für den Hamburger — erst durch den hellen Glow dahinter werden
// sie auf jedem Kartenhintergrund sichtbar (hell: Kontrast durchs Dunkle,
// dunkel: heller Schein hebt die Striche ab).
private val BurgerLineColor = Color(0xFF141414)

/**
 * Schwebendes Hamburger-Icon, sichtbar auf hellem UND dunklem Kartenhintergrund.
 * Technik („so sieht man’s auf schwarz und weiß"): ein radiales Leuchten
 * (Farbverlauf nach innen) hinter dunklen Strichen — heller Schein in der Mitte,
 * der nach außen wegfällt (keine Box). Auf hellem Hintergrund trägt die dunkle
 * Icon-Kontur, auf dunklem Hintergrund der helle Schein.
 */
@Composable
fun FloatingBurgerIcon(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val density = LocalDensity.current
    val glowRadius = with(density) { 110.dp.toPx() }
    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = 4.dp,
                shape = CircleShape,
                // weicher Schein statt harter Umrandung
                ambientColor = Color.Black.copy(alpha = 0.25f),
                spotColor = Color.Black.copy(alpha = 0.35f),
            )
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.72f),
                        Color.White.copy(alpha = 0.35f),
                        Color.White.copy(alpha = 0f),
                    ),
                    center = Offset(0.5f, 0.5f),
                    radius = glowRadius,
                ),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Menu,
            contentDescription = contentDescription,
            tint = BurgerLineColor,
        )
    }
}
