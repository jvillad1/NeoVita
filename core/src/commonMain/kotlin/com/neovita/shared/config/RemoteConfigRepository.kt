package com.neovita.shared.config

import com.neovita.shared.network.ApiService
import com.neovita.shared.network.dto.WebConfigResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RemoteConfigRepository(private val apiService: ApiService) {
    private val _config = MutableStateFlow<WebConfigResponse?>(null)
    val config: StateFlow<WebConfigResponse?> = _config.asStateFlow()

    // Failure keeps the last good config (null on a cold start, which evaluateGate treats
    // as NORMAL) — a network error must never gate the app.
    suspend fun refresh() {
        apiService.getConfig().onSuccess { _config.value = it }
    }
}
