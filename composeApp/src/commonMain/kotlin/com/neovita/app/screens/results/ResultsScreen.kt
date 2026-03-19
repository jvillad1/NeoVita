package com.neovita.app.screens.results

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.neovita.app.screens.dashboard.DashboardScreen
import com.neovita.app.ui.components.NeoProgressBar
import com.neovita.app.ui.components.ScoreRing
import com.neovita.app.ui.theme.*

class ResultsScreen : Screen {
    @Composable override fun Content() {
        val vm = koinScreenModel<ResultsViewModel>()
        val state by vm.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow

        Column(
            Modifier.fillMaxSize().background(NeoBg)
                .verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Tu Perfil de Longevidad",
                style = MaterialTheme.typography.headlineMedium,
                color = NeoNavy, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Basado en tu evaluación inicial",
                style = MaterialTheme.typography.bodyMedium, color = Color.Gray
            )
            Spacer(Modifier.height(32.dp))

            if (state.isLoading) {
                CircularProgressIndicator(color = NeoTeal700)
            } else if (state.scores != null) {
                val scores = state.scores
                ScoreRing(score = scores.overall, size = 120.dp, label = "General")
                Spacer(Modifier.height(32.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = NeoSurface)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        NeoProgressBar("Ejercicio", scores.exercise)
                        Spacer(Modifier.height(8.dp))
                        NeoProgressBar("Sueño", scores.sleep)
                        Spacer(Modifier.height(8.dp))
                        NeoProgressBar("Nutrición", scores.nutrition)
                    }
                }
                Spacer(Modifier.height(32.dp))

                Button(
                    onClick = { navigator.replace(DashboardScreen()) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeoTeal700)
                ) {
                    Text("Ver mi Plan →", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
