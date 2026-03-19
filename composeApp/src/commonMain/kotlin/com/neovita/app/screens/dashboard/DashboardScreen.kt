package com.neovita.app.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.neovita.app.ui.components.ScoreRing
import com.neovita.app.ui.theme.*

class DashboardScreen : Screen {
    @Composable override fun Content() {
        val vm = koinScreenModel<DashboardViewModel>()
        val state by vm.state.collectAsState()

        Column(
            Modifier.fillMaxSize().background(NeoBg)
                .verticalScroll(rememberScrollState()).padding(24.dp)
        ) {
            Text(
                "Hola, ${state.user?.name ?: "amigo"} 👋",
                style = MaterialTheme.typography.headlineMedium,
                color = NeoNavy, fontWeight = FontWeight.Bold
            )
            Text(
                "Tu progreso de longevidad",
                style = MaterialTheme.typography.bodyMedium, color = Color.Gray
            )
            Spacer(Modifier.height(24.dp))

            if (state.isLoading) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NeoTeal700)
                }
            } else {
                val plan = state.plan
                if (plan != null) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        ScoreRing(score = plan.scores.overall, size = 140.dp, label = "Índice General")
                    }
                    Spacer(Modifier.height(24.dp))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        PillarCard("Ejercicio", plan.scores.exercise)
                        PillarCard("Sueño", plan.scores.sleep)
                        PillarCard("Nutrición", plan.scores.nutrition)
                    }

                    if (plan.exercise.isNotEmpty()) {
                        Spacer(Modifier.height(24.dp))
                        Text(
                            "Tarea de hoy", style = MaterialTheme.typography.titleLarge,
                            color = NeoNavy, fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        plan.exercise.firstOrNull()?.let { task ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = NeoSurface)
                            ) {
                                Text(
                                    "🏃 $task", modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.bodyMedium, color = NeoNavy
                                )
                            }
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = NeoSurface)
                    ) {
                        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🌱", style = MaterialTheme.typography.displaySmall)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "¡Bienvenido a NeoVita!",
                                style = MaterialTheme.typography.titleLarge,
                                color = NeoNavy, fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Completa tu evaluación para obtener tu plan personalizado",
                                style = MaterialTheme.typography.bodyMedium, color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PillarCard(label: String, score: Int) {
    Card(
        modifier = Modifier.size(90.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NeoSurface)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ScoreRing(score = score, size = 72.dp, label = label)
        }
    }
}
