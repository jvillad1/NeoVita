package com.neovita.app.push

import kotlinx.coroutines.flow.MutableStateFlow

// Pending deep target from a tapped push notification ("/web/x" or "https://…").
// Set by platform code (MainActivity intent extra), consumed once by AppNavigation.
object PushTargetHolder {
    val target = MutableStateFlow<String?>(null)
}
