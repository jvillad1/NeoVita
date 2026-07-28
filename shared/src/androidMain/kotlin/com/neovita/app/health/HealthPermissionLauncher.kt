package com.neovita.app.health

// Health Connect exige lanzar su contrato de permisos desde una Activity registrada.
// MainActivity registra aquí su launcher (mismo patrón que CurrentActivityHolder).
object HealthPermissionLauncher {
    @Volatile
    var request: ((Set<String>, (Boolean) -> Unit) -> Unit)? = null
}
