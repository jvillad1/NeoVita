package com.neovita.shared.di

// Server origin (baseUrl without the /api suffix) for resolving same-origin relative
// URLs (e.g. SDUI OPEN_WEBVIEW targets like "/web/promo"). On the web target baseUrl
// is "/api", so the origin is "" and relative URLs stay same-origin naturally.
data class ServerOrigin(val value: String)
