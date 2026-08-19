package com.routy.app.onboarding

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.routy.app.R
import com.routy.app.RoutyApplication
import com.routy.app.auth.CaptchaWebView

@Composable
fun OnboardingScreen(onServerConfigured: () -> Unit, onSetupComplete: () -> Unit) {
    val app = LocalContext.current.applicationContext as RoutyApplication
    val viewModel: OnboardingViewModel = viewModel(
        factory = viewModelFactory {
            initializer { OnboardingViewModel(app.secureStorage, app.apiClientProvider) }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    var url by remember { mutableStateOf("") }

    LaunchedEffect(uiState) {
        when (uiState) {
            OnboardingUiState.Success -> onServerConfigured()
            OnboardingUiState.SetupComplete -> onSetupComplete()
            else -> {}
        }
    }

    when (val state = uiState) {
        is OnboardingUiState.NeedsSetup -> SetupForm(state, viewModel)
        else -> UrlForm(
            url = url,
            onUrlChange = { url = it },
            uiState = state,
            onContinue = { viewModel.checkAndSaveServerUrl(url) },
        )
    }
}

@Composable
private fun UrlForm(
    url: String,
    onUrlChange: (String) -> Unit,
    uiState: OnboardingUiState,
    onContinue: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.onboarding_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
        )

        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            label = { Text(stringResource(R.string.onboarding_url_label)) },
            placeholder = { Text(stringResource(R.string.onboarding_url_hint)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (uiState is OnboardingUiState.Error) {
            Text(
                stringResource(uiState.messageRes),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Button(
            onClick = onContinue,
            enabled = url.isNotBlank() && uiState !is OnboardingUiState.Checking,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) {
            if (uiState is OnboardingUiState.Checking) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Text(stringResource(R.string.onboarding_continue))
            }
        }
    }
}

@Composable
private fun SetupForm(state: OnboardingUiState.NeedsSetup, viewModel: OnboardingViewModel) {
    var setupToken by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var captchaToken by remember { mutableStateOf<String?>(null) }
    val deviceName = remember { "${Build.MANUFACTURER} ${Build.MODEL}".trim() }
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.setup_subtitle), style = MaterialTheme.typography.bodyMedium)

        OutlinedTextField(
            value = setupToken,
            onValueChange = { setupToken = it },
            label = { Text(stringResource(R.string.setup_token_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text(stringResource(R.string.login_username)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.login_password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text(stringResource(R.string.setup_display_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.captcha.isRequired()) {
            Text(stringResource(R.string.login_captcha_hint), style = MaterialTheme.typography.bodySmall)
            CaptchaWebView(config = state.captcha, onToken = { captchaToken = it })
        }

        if (uiState is OnboardingUiState.Error) {
            Text(
                stringResource(uiState.messageRes),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Button(
            onClick = {
                viewModel.submitSetup(
                    setupToken = setupToken,
                    username = username,
                    password = password,
                    displayName = displayName,
                    captcha = state.captcha,
                    captchaToken = captchaToken,
                    deviceName = deviceName,
                )
            },
            enabled = uiState !is OnboardingUiState.Checking,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (uiState is OnboardingUiState.Checking) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Text(stringResource(R.string.setup_submit))
            }
        }
    }
}
