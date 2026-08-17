package com.neovita.app.navigation.url

/**
 * Rutas que el spec del MVP promete en la barra del navegador. Sin esto la web no tiene
 * URLs: recargar devuelve al inicio, Atrás sale de la app y no hay enlaces compartibles.
 *
 * El mapeo vive aparte de la navegación y sin dependencias de plataforma para poder
 * probarlo: es la parte con reglas (alias, barras sobrantes, rutas desconocidas), mientras
 * que leer y escribir la URL es un detalle de cada plataforma.
 */
enum class AppRoute(val path: String) {
    LOGIN("/login"),
    ONBOARDING("/onboarding"),
    ASSESSMENT("/assessment"),
    RESULTS("/results"),
    DASHBOARD("/dashboard"),
    PLAN("/plan"),
    CHAT("/chat"),
    B2B("/b2b"),
    PROFILE("/profile");

    companion object {
        /** Ruta de la app principal cuando la URL no dice otra cosa. */
        val DEFAULT = DASHBOARD

        /**
         * Interpreta lo que haya en la barra de direcciones. Devuelve null si no es una
         * ruta nuestra — la raíz "/", una ruta vieja de un enlace compartido, o basura —
         * y quien llama decide el destino por defecto en vez de romperse.
         */
        fun fromPath(raw: String?): AppRoute? {
            if (raw.isNullOrBlank()) return null
            // Se descartan query y fragmento: "/chat?utm=x#y" es /chat.
            val soloRuta = raw.substringBefore('?').substringBefore('#')
            // Se normaliza la barra final y se compara sin distinguir mayúsculas: una URL
            // escrita a mano o copiada de un correo no tiene por qué venir perfecta.
            val normalizada = "/" + soloRuta.trim().trim('/').lowercase()
            return entries.firstOrNull { it.path == normalizada }
        }
    }
}
