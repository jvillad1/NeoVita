package com.neovita.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.Navigator
import com.neovita.app.push.PushTargetHolder
import com.neovita.app.screens.login.LoginScreen
import com.neovita.app.screens.main.MainScreen
import com.neovita.app.screens.onboarding.OnboardingScreen
import com.neovita.app.screens.web.WebContentScreen
import com.neovita.app.session.CurrentUserRole
import com.neovita.shared.session.SessionManager

@Composable
fun AppNavigation() {
    // Start screen derives from the persisted session: a stored token skips Login;
    // a brand-new sign-in routes through onboarding.
    val startScreen = remember {
        when {
            !SessionManager.isLoggedIn -> LoginScreen()
            SessionManager.pendingOnboarding -> OnboardingScreen()
            // MainScreen (not DashboardScreen) so a returning user still gets the
            // bottom tab bar — DashboardScreen is only HomeTab's content inside it.
            else -> MainScreen()
        }
    }
    val loggedIn by SessionManager.loggedIn.collectAsState()

    Navigator(startScreen) { navigator ->
        // A streak of 401s (or an explicit sign-out) clears the session — reset to Login.
        LaunchedEffect(loggedIn) {
            if (!loggedIn && navigator.lastItem !is LoginScreen) {
                // Aquí, y no en el botón de salir: cubre también la expulsión por 401,
                // que no pasa por Perfil. El rol cacheado no debe sobrevivir a la sesión.
                CurrentUserRole.clear()
                navigator.replaceAll(LoginScreen())
            }
        }
        // A tapped push can carry a web target; same rules as SDUI OPEN_WEBVIEW.
        val pushTarget by PushTargetHolder.target.collectAsState()
        LaunchedEffect(pushTarget) {
            val target = pushTarget ?: return@LaunchedEffect
            PushTargetHolder.target.value = null
            // El extra del intent es falsificable por otras apps (activity exportada);
            // solo rutas relativas (páginas de nuestro propio servidor) se rutean — una
            // URL externa nunca se renderiza bajo el chrome de NeoVita.
            if (target.startsWith("/") && !target.startsWith("//")) {
                navigator.push(WebContentScreen(title = "NeoVita", url = target))
            }
        }
        CurrentScreen()
    }
}
