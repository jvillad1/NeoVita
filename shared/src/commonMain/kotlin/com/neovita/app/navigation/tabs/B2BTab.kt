package com.neovita.app.navigation.tabs

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.neovita.app.screens.b2b.B2BScreen

object B2BTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(index = 3u, title = "Empresa")

    @Composable
    override fun Content() = B2BScreen().Content()
}
