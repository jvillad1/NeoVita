package com.neovita.app.ui.web

// El WebView de wasm abre una pestaña nueva (no un WebView embebido), y esa pestaña no
// lleva el token de sesión: cualquier página autenticada respondería 401 ahí.
actual fun supportsAuthenticatedWebView(): Boolean = false
