package com.neovita.app.navigation.url

import kotlinx.browser.window

actual object BrowserUrl {
    actual fun currentPath(): String? = window.location.pathname

    actual fun push(route: AppRoute) {
        // Sin esta comparación, volver a tocar la pestaña actual apilaría una entrada más
        // en el historial y haría falta pulsar Atrás varias veces para salir de ella.
        if (window.location.pathname == route.path) return
        window.history.pushState(null, "", route.path)
    }

    actual fun onBackForward(listener: (AppRoute?) -> Unit) {
        window.addEventListener("popstate", {
            listener(AppRoute.fromPath(window.location.pathname))
        })
    }
}
