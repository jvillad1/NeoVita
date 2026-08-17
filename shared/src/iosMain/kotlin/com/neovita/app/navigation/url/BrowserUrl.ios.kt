package com.neovita.app.navigation.url

/** En ios no hay barra de direcciones: la navegación la lleva entera Voyager. */
actual object BrowserUrl {
    actual fun currentPath(): String? = null
    actual fun push(route: AppRoute) = Unit
    actual fun onBackForward(listener: (AppRoute?) -> Unit) = Unit
}
