package com.neovita.app.navigation.tabs

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.neovita.app.screens.dashboard.DashboardScreen

object HomeTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(index = 0u, title = "Home")

    @Composable
    override fun Content() = DashboardScreen().Content()
}
