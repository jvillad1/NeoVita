package com.neovita.app.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import org.koin.compose.koinInject
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.neovita.app.screens.assessment.AssessmentScreen
import com.neovita.app.ui.components.ErrorBanner
import com.neovita.app.ui.theme.*

class OnboardingScreen : Screen {
    @Composable override fun Content() {
        val vm: OnboardingViewModel = koinInject()
        val state by vm.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow

        LaunchedEffect(state.done) { if (state.done) navigator.replace(AssessmentScreen()) }

        Column(
            Modifier.fillMaxSize().background(NeoBg).padding(32.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "¡Hola! Cuéntanos sobre ti",
                style = MaterialTheme.typography.headlineMedium,
                color = NeoNavy, fontWeight = FontWeight.Bold
            )
            Text(
                "Solo lo básico para personalizar tu experiencia",
                style = MaterialTheme.typography.bodyMedium, color = Color.Gray
            )
            Spacer(Modifier.height(32.dp))
            OutlinedTextField(
                value = state.name, onValueChange = vm::onNameChange,
                label = { Text("Tu nombre") }, modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = state.age, onValueChange = vm::onAgeChange,
                label = { Text("Tu edad") }, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
            if (state.error != null) ErrorBanner(state.error!!, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = vm::save, modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = !state.isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = NeoTeal700)
            ) {
                if (state.isLoading)
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                else
                    Text("Continuar →", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
