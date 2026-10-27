package com.routy.app.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.routy.app.ui.theme.ChromeCard
import com.routy.app.ui.theme.ChromeCardDark
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * Schwebende Hinweise oben links, die die Karte nicht verschieben (Update-,
 * Upload-, Offline-Status — §3/§4.4). Nur Nachrichten; keine Navigation.
 */
@Composable
fun FloatingNotices(
    notices: List<String>,
    modifier: Modifier = Modifier,
) {
    if (notices.isEmpty()) return
    val dark = isSystemInDarkTheme()
    Column(
        modifier = modifier.padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        notices.forEach { notice ->
            Text(
                text = notice,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Start,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .background(
                        color = if (dark) ChromeCardDark else ChromeCard,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .widthIn(max = 240.dp),
            )
        }
    }
}
