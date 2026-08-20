package com.routy.app.settings

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.routy.app.R
import com.routy.app.RoutyApplication
import com.routy.app.webview.AuthenticatedWebSheet
import com.routy.app.ui.OfflineBanner
import com.routy.app.logic.api.SessionListEntry
import com.routy.app.logic.time.parseServerInstant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val LOCALES = listOf("de" to R.string.settings_locale_de, "en" to R.string.settings_locale_en)
private val THEMES = listOf(
    "auto" to R.string.settings_theme_auto,
    "light" to R.string.settings_theme_light,
    "dark" to R.string.settings_theme_dark,
)

@Composable
fun SettingsScreen(
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.applicationContext as RoutyApplication
    val activity = LocalContext.current as? Activity
    val viewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SettingsViewModel(app.apiClientProvider, app.bootstrapLoader) }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    var showAccountSheet by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.needsRecreate) {
        if (uiState.needsRecreate) {
            viewModel.consumeRecreate()
            activity?.recreate()
        }
    }

    if (uiState.loading) {
        Column(
            modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge)
            uiState.user?.let { user ->
                Text(
                    text = "${user.displayName} (@${user.username})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (uiState.error) {
            item {
                Text(
                    text = stringResource(R.string.common_error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                CompactOutlinedButton(onClick = viewModel::load, enabled = !uiState.saving, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.stats_retry), style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        uiState.messageRes?.let { res ->
            item {
                Text(stringResource(res), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }

        item {
            if (uiState.offlineCached) {
                OfflineBanner()
            }
        }

        item {
            SettingsSection(title = stringResource(R.string.settings_account_security_title)) {
                Text(
                    stringResource(R.string.settings_account_security_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CompactOutlinedButton(
                    onClick = { showAccountSheet = true },
                    enabled = !uiState.saving && app.secureStorage.serverUrl != null && app.secureStorage.token != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.settings_account_security_open), style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        item {
            SettingsSection(title = stringResource(R.string.settings_language)) {
                LocaleDropdown(
                    current = uiState.user?.locale ?: "de",
                    enabled = !uiState.saving,
                    onSelect = viewModel::setLocale,
                )
            }
        }

        item {
            SettingsSection(title = stringResource(R.string.settings_theme)) {
                ThemeDropdown(
                    current = uiState.user?.theme ?: "auto",
                    enabled = !uiState.saving,
                    onSelect = viewModel::setTheme,
                )
            }
        }

        item {
            SettingsSection(title = stringResource(R.string.settings_walk_speed_title)) {
                WalkSpeedField(
                    current = uiState.user?.walkSpeedKmh,
                    networkDefault = uiState.networkWalkSpeedKmh,
                    enabled = !uiState.saving,
                    onSave = viewModel::setWalkSpeed,
                )
            }
        }

        item {
            SettingsSection(title = stringResource(R.string.settings_crash_consent_title)) {
                var consent by remember { mutableStateOf(app.secureStorage.crashReportConsent) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.settings_crash_consent_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = consent,
                        onCheckedChange = {
                            consent = it
                            app.applyCrashReportConsent(it)
                        },
                    )
                }
            }
        }

        item {
            SettingsSection(title = stringResource(R.string.settings_sessions)) {
                if (uiState.sessions.isEmpty()) {
                    Text(stringResource(R.string.settings_sessions_empty), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        items(uiState.sessions) { session ->
            SessionRow(
                session = session,
                enabled = !uiState.saving,
                onRevoke = { viewModel.revokeSession(session.sessionId) },
            )
        }

        item {
            CompactOutlinedButton(
                onClick = viewModel::revokeOtherSessions,
                enabled = !uiState.saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_logout_everywhere), style = MaterialTheme.typography.labelMedium)
            }
        }

        item {
            CompactButton(
                onClick = onSignOut,
                enabled = !uiState.saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_logout), style = MaterialTheme.typography.labelMedium)
            }
        }
    }

    if (showAccountSheet) {
        val base = app.secureStorage.serverUrl?.trimEnd('/')
        val token = app.secureStorage.token
        if (base != null && token != null) {
            AuthenticatedWebSheet(
                baseUrl = base,
                token = token,
                path = "/settings/account",
                onDismiss = { showAccountSheet = false },
                onNavigateToLogin = {
                    showAccountSheet = false
                    onSignOut()
                },
            )
        } else {
            showAccountSheet = false
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocaleDropdown(current: String, enabled: Boolean, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = LOCALES.firstOrNull { it.first == current }?.second ?: R.string.settings_locale_de

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = it }) {
        Row(
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
        }
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LOCALES.forEach { (code, res) ->
                DropdownMenuItem(
                    text = { Text(stringResource(res)) },
                    onClick = {
                        expanded = false
                        if (code != current) onSelect(code)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeDropdown(current: String, enabled: Boolean, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = THEMES.firstOrNull { it.first == current }?.second ?: R.string.settings_theme_auto

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = it }) {
        Row(
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
        }
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            THEMES.forEach { (code, res) ->
                DropdownMenuItem(
                    text = { Text(stringResource(res)) },
                    onClick = {
                        expanded = false
                        if (code != current) onSelect(code)
                    },
                )
            }
        }
    }
}

@Composable
private fun WalkSpeedField(
    current: Double?,
    networkDefault: Double,
    enabled: Boolean,
    onSave: (Double?) -> Unit,
) {
    var text by remember(current) {
        mutableStateOf(current?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "")
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.settings_walk_speed_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter { ch -> ch.isDigit() || ch == '.' } },
                enabled = enabled,
                singleLine = true,
                label = { Text(stringResource(R.string.settings_walk_speed_label)) },
                placeholder = { Text("%.1f".format(networkDefault)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            CompactOutlinedButton(
                onClick = {
                    val trimmed = text.trim()
                    val value = if (trimmed.isEmpty()) null else trimmed.toDoubleOrNull()
                    if (trimmed.isEmpty() || (value != null && value > 0)) onSave(value)
                },
                enabled = enabled,
            ) {
                Text(stringResource(R.string.settings_walk_speed_save), style = MaterialTheme.typography.labelMedium)
            }
        }
        Text(
            stringResource(R.string.settings_walk_speed_hint, networkDefault),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SessionRow(
    session: com.routy.app.logic.api.SessionListEntry,
    enabled: Boolean,
    onRevoke: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.deviceName ?: session.client,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (session.isCurrent) FontWeight.Bold else FontWeight.Normal,
                )
                Text(
                    text = formatSessionDate(session.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (session.isCurrent) {
                    Text(
                        text = stringResource(R.string.settings_session_current),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (!session.isCurrent) {
                TextButton(onClick = onRevoke, enabled = enabled) {
                    Text(stringResource(R.string.settings_session_revoke), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

private fun formatSessionDate(iso: String): String {
    val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault())
    return formatter.format(parseServerInstant(iso))
}

private val CompactPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)

@Composable
private fun CompactButton(onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier, contentPadding = CompactPadding, content = content)
}

@Composable
private fun CompactOutlinedButton(onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier, contentPadding = CompactPadding, content = content)
}
