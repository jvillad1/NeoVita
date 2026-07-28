package com.neovita.app.health

import com.neovita.shared.network.ApiService

actual class HealthSyncClient actual constructor() {
    actual fun isAvailable(): Boolean = false
    actual suspend fun requestPermissions(): Boolean = false
    actual suspend fun sync(apiService: ApiService): HealthSyncState = HealthSyncState.UNAVAILABLE
}
