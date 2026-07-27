package com.neovita.app.ui.web

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// In-app web renderer for SDUI OPEN_WEBVIEW slots. The session JWT is attached as an
// Authorization header on the INITIAL request only (subresource requests don't carry it) —
// enough for server-rendered pages; SPAs served here must not rely on that header. The
// header is attached ONLY for same-origin targets (attachSession); an external https page
// never receives the session JWT.
@Composable
expect fun PlatformWebView(url: String, attachSession: Boolean, modifier: Modifier)
