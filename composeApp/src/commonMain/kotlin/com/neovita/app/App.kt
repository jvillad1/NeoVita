package com.neovita.app

import androidx.compose.runtime.Composable
import com.neovita.app.navigation.AppNavigation
import com.neovita.app.ui.theme.NeoVitaTheme
import com.neovita.shared.di.sharedModule
import org.koin.compose.KoinApplication

@Composable
fun App(baseUrl: String = "http://localhost:8080") {
    KoinApplication(application = { modules(sharedModule(baseUrl)) }) {
        NeoVitaTheme {
            // Default: not logged in — LoginScreen handles the flow
            AppNavigation(isLoggedIn = false, isNewUser = false)
        }
    }
}
