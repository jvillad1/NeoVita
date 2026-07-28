package com.neovita.app.config

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import com.neovita.app.push.activatePush
import com.neovita.shared.config.ClientInfo
import com.neovita.shared.config.GateState
import com.neovita.shared.config.RemoteConfigRepository
import com.neovita.shared.config.evaluateGate
import com.neovita.shared.network.ApiService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.time.Duration.Companion.minutes

@Composable
fun ConfigGate(clientInfo: ClientInfo, content: @Composable () -> Unit) {
    val repo = koinInject<RemoteConfigRepository>()
    val apiService = koinInject<ApiService>()
    val config by repo.config.collectAsState()
    val scope = rememberCoroutineScope()

    // Startup fetch + 5-minute ticker (no common lifecycle hook in this stack; the
    // ticker also picks up "maintenance over" without user action).
    LaunchedEffect(Unit) {
        while (true) {
            repo.refresh()
            delay(5.minutes)
        }
    }

    // Dormant push: activates only when the server serves Firebase config + the flag.
    LaunchedEffect(config) { activatePush(config, apiService) }

    // content() stays composed while gated so the user's navigation stack survives a
    // temporary maintenance window; the gate screens are opaque and swallow input.
    Box {
        content()
        when (evaluateGate(config, clientInfo)) {
            GateState.MAINTENANCE -> MaintenanceScreen(onRetry = { scope.launch { repo.refresh() } })
            GateState.UPDATE_REQUIRED -> UpdateRequiredScreen()
            GateState.NORMAL -> {}
        }
    }
}
