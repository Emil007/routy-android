package com.routy.app.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.routy.app.R
import com.routy.app.RoutyApplication
import com.routy.app.logic.api.SuggestNamePartsRequest

private data class NamePartChip(val speakText: String, val displayText: String)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NamePartsInput(
    lat: Double,
    lng: Double,
    part1: String,
    part2: String,
    onPart1: (String) -> Unit,
    onPart2: (String) -> Unit,
    modifier: Modifier = Modifier,
    prefillPart1: Boolean = true,
) {
    val apiClientProvider = (LocalContext.current.applicationContext as RoutyApplication).apiClientProvider
    var chips by remember(lat, lng) { mutableStateOf<List<NamePartChip>>(emptyList()) }

    LaunchedEffect(lat, lng) {
        chips = emptyList()
        val res = runCatching {
            apiClientProvider.service.suggestNameParts(SuggestNamePartsRequest(lat, lng))
        }.getOrNull()
        if (res?.isSuccessful != true) return@LaunchedEffect
        val body = res.body() ?: return@LaunchedEffect
        val options = mutableListOf<NamePartChip>()
        val osmSpeak = body.osmSpeakText ?: body.osmText
        val osmDisplay = body.osmDisplayText ?: osmSpeak
        if (!osmSpeak.isNullOrBlank() && !osmDisplay.isNullOrBlank()) {
            options.add(NamePartChip(osmSpeak, osmDisplay))
        }
        for (p in body.nearbyParts) {
            val speak = p.speakText ?: p.text
            val display = p.displayText ?: p.text
            if (options.none { it.speakText == speak }) {
                options.add(NamePartChip(speak, display))
            }
        }
        chips = options
        if (prefillPart1 && osmSpeak != null && part1.isBlank()) onPart1(osmSpeak)
    }

    @Composable
    fun ChipRow(onPick: (String) -> Unit) {
        if (chips.isEmpty()) return
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            chips.forEach { chip ->
                FilterChip(
                    selected = false,
                    onClick = { onPick(chip.speakText) },
                    label = { Text(chip.displayText, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = part1,
            onValueChange = onPart1,
            placeholder = { Text(stringResource(R.string.record_name_part1)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        ChipRow(onPart1)
        OutlinedTextField(
            value = part2,
            onValueChange = onPart2,
            placeholder = { Text(stringResource(R.string.record_name_part2)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        ChipRow(onPart2)
    }
}
