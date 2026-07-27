package com.neovita.app.ui.web

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private fun openInNewTab(url: String): Unit = js("{ window.open(url, '_blank'); }")

@Composable
actual fun PlatformWebView(url: String, attachSession: Boolean, modifier: Modifier) {
    // The wasm app already runs in a browser: open the page in a new tab (popup blockers
    // may require the explicit button if the automatic attempt is suppressed).
    LaunchedEffect(url) { openInNewTab(url) }
    Column(modifier, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Este contenido se abre en una pestaña nueva.")
        Spacer(Modifier.height(16.dp))
        Button(onClick = { openInNewTab(url) }) { Text("Abrir de nuevo") }
    }
}
