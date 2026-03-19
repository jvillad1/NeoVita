package com.neovita.app.screens.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.neovita.app.screens.onboarding.OnboardingScreen
import com.neovita.app.screens.dashboard.DashboardScreen
import com.neovita.app.ui.components.ErrorBanner
import com.neovita.app.ui.theme.*

class LoginScreen : Screen {
    @Composable override fun Content() {
        val vm = koinScreenModel<LoginViewModel>()
        val state by vm.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow

        LaunchedEffect(state.success) {
            state.success?.let { auth ->
                navigator.replace(if (auth.isNewUser) OnboardingScreen() else DashboardScreen())
            }
        }

        Column(
            Modifier.fillMaxSize().background(NeoBg).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo
            Box(
                Modifier.size(80.dp).clip(RoundedCornerShape(20.dp))
                    .background(brush = Brush.linearGradient(listOf(NeoTeal900, NeoTeal500))),
                contentAlignment = Alignment.Center
            ) {
                Text("🌿", style = MaterialTheme.typography.displaySmall)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "NeoVita", style = MaterialTheme.typography.headlineLarge,
                color = NeoTeal900, fontWeight = FontWeight.Bold
            )
            Text(
                "Coaching Integral con IA para la Longevidad",
                style = MaterialTheme.typography.bodyMedium, color = Color.Gray,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(48.dp))

            if (state.isLoading) {
                CircularProgressIndicator(color = NeoTeal700)
            } else {
                Button(
                    onClick = vm::signInWithGoogle,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeoTeal700)
                ) {
                    Text("Continuar con Google", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
            state.error?.let { ErrorBanner(it, modifier = Modifier.padding(top = 16.dp)) }
        }
    }
}
