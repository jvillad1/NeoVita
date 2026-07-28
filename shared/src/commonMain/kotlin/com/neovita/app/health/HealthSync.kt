package com.neovita.app.health

import com.neovita.shared.network.ApiService

enum class HealthSyncState { UNAVAILABLE, NEEDS_PERMISSION, SYNCING, SYNCED, ERROR }

// Lectura de datos de salud del dispositivo. Siempre iniciada por la usuaria (nunca al
// arrancar): son datos sensibles. Plataformas sin soporte devuelven UNAVAILABLE.
expect class HealthSyncClient() {
    fun isAvailable(): Boolean
    suspend fun requestPermissions(): Boolean
    suspend fun sync(apiService: ApiService): HealthSyncState
}
