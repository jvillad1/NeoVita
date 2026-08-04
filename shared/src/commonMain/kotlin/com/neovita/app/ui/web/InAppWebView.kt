package com.neovita.app.ui.web

/** ¿Puede esta plataforma abrir una página autenticada dentro de la app? En web no:
 *  el WebView de wasm abre una pestaña nueva y ahí no viaja el token de sesión. */
expect fun supportsAuthenticatedWebView(): Boolean
