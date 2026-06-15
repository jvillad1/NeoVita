package com.neovita.app

import androidx.compose.runtime.Composable
import com.neovita.app.navigation.AppNavigation
import com.neovita.app.ui.theme.NeoVitaTheme
import com.neovita.app.di.appModule
import com.neovita.shared.data.cache.LocalCache
import com.neovita.shared.di.sharedModule
import org.koin.compose.KoinApplication

@Composable
fun App(baseUrl: String = "http://localhost:8080", cache: LocalCache? = null) {
    KoinApplication(application = { modules(sharedModule(baseUrl, cache), appModule) }) {
        NeoVitaTheme {
            // Start screen + forced-logout handling derive from the persisted session.
            AppNavigation()
        }
    }
}
