package com.neovita.app.screens.web

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.neovita.app.ui.web.PlatformWebView
import com.neovita.shared.di.ServerOrigin
import org.koin.compose.koinInject

// A server-deployed HTML page rendered in-app: the "deploy web into the installed app"
// slot of the install-once strategy. Secondary surfaces only — core flows stay native.
data class WebContentScreen(val title: String, val url: String) : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val origin = koinInject<ServerOrigin>().value
        val resolvedUrl = if (url.startsWith("http")) url else origin + url

        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
            PlatformWebView(resolvedUrl, Modifier.fillMaxWidth().weight(1f))
        }
    }
}
