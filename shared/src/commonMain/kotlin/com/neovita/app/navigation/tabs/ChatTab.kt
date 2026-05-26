package com.neovita.app.navigation.tabs

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.neovita.app.screens.chat.ChatScreen

object ChatTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(index = 1u, title = "Chat")

    @Composable
    override fun Content() = ChatScreen().Content()
}
