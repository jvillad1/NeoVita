package com.neovita.app.ui.web

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import com.neovita.shared.session.SessionManager
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.setValue
import platform.WebKit.WKWebView

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformWebView(url: String, modifier: Modifier) {
    UIKitView(
        modifier = modifier,
        factory = {
            val webView = WKWebView()
            NSURL.URLWithString(url)?.let { nsUrl ->
                val request = NSMutableURLRequest(uRL = nsUrl)
                SessionManager.token?.let {
                    request.setValue("Bearer $it", forHTTPHeaderField = "Authorization")
                }
                webView.loadRequest(request)
            }
            webView
        }
    )
}
