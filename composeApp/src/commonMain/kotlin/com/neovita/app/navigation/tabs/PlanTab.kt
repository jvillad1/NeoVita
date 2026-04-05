package com.neovita.app.navigation.tabs

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.neovita.app.screens.plan.PlanScreen

object PlanTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(index = 2u, title = "Plan")

    @Composable
    override fun Content() = PlanScreen().Content()
}
