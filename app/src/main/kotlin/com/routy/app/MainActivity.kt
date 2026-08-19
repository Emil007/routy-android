package com.routy.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.routy.app.auth.LoginScreen
import com.routy.app.onboarding.OnboardingScreen
import com.routy.app.recording.RecordingScreen
import com.routy.app.ui.theme.RoutyTheme
import com.routy.app.webview.RoutyShellScreen

private object Routes {
    const val ONBOARDING = "onboarding"
    const val LOGIN = "login"
    const val SHELL = "shell"
    const val RECORDING = "recording"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as RoutyApplication
        val startDestination = when {
            app.secureStorage.serverUrl == null -> Routes.ONBOARDING
            app.secureStorage.token == null -> Routes.LOGIN
            else -> Routes.SHELL
        }

        setContent {
            RoutyTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RoutyNavHost(startDestination)
                }
            }
        }
    }
}

@Composable
private fun RoutyNavHost(startDestination: String) {
    val navController: NavHostController = rememberNavController()

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onServerConfigured = {
                    navController.navigate(Routes.LOGIN) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                },
            )
        }
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoggedIn = {
                    navController.navigate(Routes.SHELL) { popUpTo(Routes.LOGIN) { inclusive = true } }
                },
            )
        }
        composable(Routes.SHELL) {
            RoutyShellScreen(
                onSignedOut = {
                    navController.navigate(Routes.LOGIN) { popUpTo(Routes.SHELL) { inclusive = true } }
                },
                onStartRecording = { navController.navigate(Routes.RECORDING) },
            )
        }
        composable(Routes.RECORDING) {
            RecordingScreen(onDone = { navController.popBackStack() })
        }
    }
}
