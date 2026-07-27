package com.neovita.app.ui.web

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.neovita.shared.session.SessionManager

@Composable
actual fun PlatformWebView(url: String, attachSession: Boolean, modifier: Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                // Keep navigation inside the WebView instead of launching the browser.
                webViewClient = WebViewClient()
                val headers = if (attachSession) {
                    SessionManager.token?.let { mapOf("Authorization" to "Bearer $it") } ?: emptyMap()
                } else emptyMap()
                loadUrl(url, headers)
            }
        }
    )
}
