package com.neovita.app.screens.b2b

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import com.neovita.app.ui.components.ErrorBanner
import com.neovita.app.ui.theme.*
import org.koin.compose.koinInject

class B2BScreen : Screen {
    @Composable override fun Content() {
        val vm: B2BViewModel = koinInject()
        val state by vm.state.collectAsState()

        Column(Modifier.fillMaxSize().background(NeoBg).padding(24.dp)) {
            Text(
                "Panel de Empresa",
                style = MaterialTheme.typography.headlineMedium,
                color = NeoNavy, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Monitorea el bienestar de tu equipo",
                style = MaterialTheme.typography.bodyMedium, color = Color.Gray
            )
            Spacer(Modifier.height(16.dp))

            state.error?.let {
                ErrorBanner(it)
                Spacer(Modifier.height(16.dp))
            }

            when {
                state.isLoading -> Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                    CircularProgressIndicator(color = NeoTeal700)
                }
                state.team.totalMembers == 0 && state.error == null -> Text(
                    "Todavía no hay nadie en tu empresa.",
                    style = MaterialTheme.typography.bodyMedium, color = Color.Gray
                )
                state.error == null -> TeamSummary(state)
            }
        }
    }
}

@Composable
private fun TeamSummary(state: B2BState) {
    val team = state.team
    val averages = team.averages
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NeoSurface)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem("Promedio", averages?.overall?.toString() ?: "—")
            StatItem("Evaluados", "${team.evaluatedCount}")
            StatItem("Total", "${team.totalMembers}")
        }
    }
    Spacer(Modifier.height(16.dp))

    if (averages == null) {
        // Sin el mínimo de respuestas no hay promedio que mostrar: con pocas personas el
        // promedio deja ver el puntaje de cada una.
        Text(
            "Los resultados del equipo aparecen cuando al menos ${team.minGroupSize} " +
                "personas hayan respondido la evaluación. Faltan ${state.evaluacionesQueFaltan}.",
            style = MaterialTheme.typography.bodyMedium, color = NeoNavy
        )
    } else {
        Text(
            "Promedio por pilar",
            style = MaterialTheme.typography.titleLarge,
            color = NeoNavy, fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = NeoSurface)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem("Ejercicio", "${averages.exercise}")
                StatItem("Sueño", "${averages.sleep}")
                StatItem("Nutrición", "${averages.nutrition}")
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    Text(
        "Por privacidad, NeoVita sólo muestra resultados agregados del equipo: nunca el " +
            "puntaje, el nombre ni las respuestas de una persona.",
        style = MaterialTheme.typography.bodySmall, color = Color.Gray
    )
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium,
            color = NeoTeal700, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
    }
}
