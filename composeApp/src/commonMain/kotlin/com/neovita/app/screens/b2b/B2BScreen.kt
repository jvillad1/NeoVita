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
import cafe.adriel.voyager.koin.koinScreenModel
import com.neovita.app.ui.components.ErrorBanner
import com.neovita.app.ui.theme.*

data class TeamMember(val name: String, val email: String, val score: Int)

data class B2BState(
    val members: List<TeamMember> = emptyList(),
    val avgScore: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null
)

class B2BScreen : Screen {
    @Composable override fun Content() {
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

            // Placeholder stats card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = NeoSurface)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem("Promedio", "--")
                    StatItem("Activos", "--")
                    StatItem("Total", "--")
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Empleados",
                style = MaterialTheme.typography.titleLarge,
                color = NeoNavy, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Conecta el servidor para ver datos del equipo",
                style = MaterialTheme.typography.bodyMedium, color = Color.Gray
            )
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
