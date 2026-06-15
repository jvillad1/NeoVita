package com.neovita.shared.session

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Global session/auth store, mirroring Movi's SessionManager but adapted to NeoVita's
 * stack: persistence via multiplatform-settings, reactive state via a [StateFlow]
 * (NeoVita's UI observes StateFlow, not Compose state).
 *
 * The auth token is persisted, so a session survives app restarts. The Ktor client
 * (wired in `sharedModule`) injects [token] on every request and reports 4xx/2xx back
 * here so a streak of 401s logs the user out automatically.
 */
object SessionManager {
    private val settings: Settings by lazy { Settings() }

    private const val KEY_TOKEN = "auth_token"

    private val _loggedIn = MutableStateFlow(!settings.getStringOrNull(KEY_TOKEN).isNullOrBlank())
    /** Reactive login state — App() resets to Login when this turns false. */
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    var token: String?
        get() = settings.getStringOrNull(KEY_TOKEN)
        private set(value) {
            if (value == null) settings.remove(KEY_TOKEN) else settings[KEY_TOKEN] = value
        }

    val isLoggedIn: Boolean get() = !token.isNullOrBlank()

    /**
     * Transient hint (not persisted) set right after a fresh sign-in so the start
     * screen can route a brand-new user to onboarding. Restarts default to false.
     */
    var pendingOnboarding: Boolean = false

    private var consecutive401s = 0
    private const val MAX_CONSECUTIVE_401S = 3

    /** Call on every successful authenticated response to reset the 401 streak. */
    fun onAuthSuccess() { consecutive401s = 0 }

    /**
     * Call on every 401. Clears the session only after [MAX_CONSECUTIVE_401S] consecutive
     * failures — avoids logging out on a single transient 401. Network errors (no
     * connectivity) must NOT call this, so the app keeps working offline.
     */
    fun onUnauthorized() {
        consecutive401s++
        if (consecutive401s >= MAX_CONSECUTIVE_401S) clear()
    }

    fun login(token: String) {
        this.token = token
        consecutive401s = 0
        _loggedIn.value = true
    }

    fun clear() {
        token = null
        pendingOnboarding = false
        consecutive401s = 0
        _loggedIn.value = false
    }
}
