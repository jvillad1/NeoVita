package com.neovita.app.navigation.url

/**
 * Acceso a la barra de direcciones. Sólo la web tiene una: en Android e iOS estas funciones
 * no hacen nada, y ese es el comportamiento correcto, no una carencia.
 */
expect object BrowserUrl {
    /** Ruta actual, o null donde no hay navegador. */
    fun currentPath(): String?

    /** Refleja [route] en la barra sin recargar. No apila si ya estamos ahí. */
    fun push(route: AppRoute)

    /** Avisa cuando el usuario usa Atrás/Adelante del navegador. */
    fun onBackForward(listener: (AppRoute?) -> Unit)
}
