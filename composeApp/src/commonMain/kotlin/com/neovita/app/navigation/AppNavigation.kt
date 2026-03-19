package com.neovita.app.navigation

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.Navigator
import com.neovita.app.screens.login.LoginScreen
import com.neovita.app.screens.dashboard.DashboardScreen
import com.neovita.app.screens.onboarding.OnboardingScreen

@Composable
fun AppNavigation(isLoggedIn: Boolean, isNewUser: Boolean) {
    val startScreen = when {
        !isLoggedIn -> LoginScreen()
        isNewUser -> OnboardingScreen()
        else -> DashboardScreen()
    }
    Navigator(startScreen)
}
