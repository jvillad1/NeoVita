package com.neovita.app.health

// Health Connect exige lanzar su contrato de permisos desde una Activity registrada.
// El callback pendiente vive AQUÍ y no en la Activity: si el sistema la recrea (rotación)
// mientras el diálogo está abierto, el resultado se entrega a la instancia nueva y la
// corrutina que espera sigue viva.
object HealthPermissionLauncher {
    @Volatile
    var request: ((Set<String>, (Boolean) -> Unit) -> Unit)? = null

    // Identidad de la Activity que registró el launcher (para no borrar el de una nueva).
    @Volatile
    var owner: Any? = null

    @Volatile
    private var pending: ((Boolean) -> Unit)? = null

    fun setPending(callback: (Boolean) -> Unit) {
        pending = callback
    }

    /** Entrega el resultado una sola vez; si no hay nadie esperando, no hace nada. */
    fun deliver(granted: Boolean) {
        val callback = pending
        pending = null
        callback?.invoke(granted)
    }
}
