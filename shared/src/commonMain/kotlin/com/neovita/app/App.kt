package com.neovita.app

import androidx.compose.runtime.Composable
import com.neovita.app.config.ConfigGate
import com.neovita.app.navigation.AppNavigation
import com.neovita.app.ui.theme.NeoVitaTheme
import com.neovita.app.di.appModule
import com.neovita.shared.config.AppPlatform
import com.neovita.shared.config.ClientInfo
import com.neovita.shared.data.cache.LocalCache
import com.neovita.shared.di.sharedModule
import org.koin.compose.KoinApplication

@Composable
fun App(
    baseUrl: String = "http://localhost:8080",
    cache: LocalCache? = null,
    clientInfo: ClientInfo = ClientInfo(AppPlatform.WEB, 0)
) {
    KoinApplication(application = { modules(sharedModule(baseUrl, cache), appModule) }) {
        NeoVitaTheme {
            // Remote-config gate: maintenance / forced-update screens take over the UI
            // when the server says so; otherwise normal navigation.
            ConfigGate(clientInfo) {
                // Start screen + forced-logout handling derive from the persisted session.
                AppNavigation()
            }
        }
    }
}
