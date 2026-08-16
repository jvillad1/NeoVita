package com.neovita.app.screens.b2b

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import com.neovita.app.ui.components.ErrorBanner
import com.neovita.app.ui.theme.*
import com.neovita.shared.network.dto.TeamMemberDto
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

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = NeoSurface)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // El promedio sólo tiene sentido si alguien respondió; con cero
                    // evaluaciones el servidor manda 0 y mostrarlo como "0" haría pensar
                    // que el equipo puntúa pésimo, cuando lo que pasa es que no hay datos.
                    val hayDatos = state.members.any { it.scores != null }
                    StatItem("Promedio", if (hayDatos) "${state.avgScore}" else "—")
                    StatItem("Evaluados", "${state.members.size - state.sinEvaluar}")
                    StatItem("Total", "${state.members.size}")
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Empleados",
                style = MaterialTheme.typography.titleLarge,
                color = NeoNavy, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))

            when {
                state.isLoading -> Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                    CircularProgressIndicator(color = NeoTeal700)
                }
                state.members.isEmpty() && state.error == null -> Text(
                    "Todavía no hay nadie en tu empresa.",
                    style = MaterialTheme.typography.bodyMedium, color = Color.Gray
                )
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.members) { MemberRow(it) }
                }
            }
        }
    }
}

@Composable
private fun MemberRow(member: TeamMemberDto) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NeoSurface)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(NeoTeal200),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    member.name.take(1).uppercase(),
                    color = NeoTeal700, fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(member.name, color = NeoNavy, fontWeight = FontWeight.Medium)
                Text(
                    member.email,
                    style = MaterialTheme.typography.bodySmall, color = Color.Gray
                )
            }
            // "Sin evaluar" en vez de un 0: quien no respondió no puntúa cero, no puntúa.
            val scores = member.scores
            if (scores == null) {
                Text(
                    "Sin evaluar",
                    style = MaterialTheme.typography.bodySmall, color = Color.Gray
                )
            } else {
                Text(
                    "${scores.overall}",
                    style = MaterialTheme.typography.titleLarge,
                    color = NeoTeal700, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium,
            color = NeoTeal700, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
    }
}
