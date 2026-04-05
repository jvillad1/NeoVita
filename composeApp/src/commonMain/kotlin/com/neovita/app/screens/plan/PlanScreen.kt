package com.neovita.app.screens.plan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import org.koin.compose.koinInject
import com.neovita.app.ui.components.ErrorBanner
import com.neovita.app.ui.theme.*

private data class PlanSectionTheme(val gradient: List<Color>)

private fun sectionTheme(title: String): PlanSectionTheme = when {
    title.contains("Nutri", ignoreCase = true) ->
        PlanSectionTheme(listOf(Color(0xFF2D6A2D), Color(0xFF1A4A3A)))
    title.contains("Sue", ignoreCase = true) ->
        PlanSectionTheme(listOf(Color(0xFF1A2A5E), Color(0xFF2A1A4E)))
    title.contains("Ejerc", ignoreCase = true) ->
        PlanSectionTheme(listOf(Color(0xFF1A3A2A), Color(0xFF0D4A3A)))
    title.contains("Mental", ignoreCase = true) || title.contains("Salud", ignoreCase = true) ->
        PlanSectionTheme(listOf(Color(0xFF2A1A3A), Color(0xFF1A2A5E)))
    else ->
        PlanSectionTheme(listOf(NeoDarkSurface2, NeoDarkSurface))
}

class PlanScreen : Screen {
    @Composable
    override fun Content() {
        val vm: PlanViewModel = koinInject()
        val state by vm.state.collectAsState()

        Box(
            Modifier
                .fillMaxSize()
                .background(NeoDarkBg)
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                // Header
                item {
                    Column(
                        Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 16.dp)
                    ) {
                        Text(
                            "NeoVita Daily Plan",
                            style = MaterialTheme.typography.headlineMedium,
                            color = NeoTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Tu plan personalizado de longevidad",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NeoTextSecondary
                        )
                    }
                }

                // Loading state
                if (state.isLoading) {
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = NeoCrimson)
                        }
                    }
                } else {
                    // Generating state
                    if (state.isGenerating) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 8.dp),
                                colors = CardDefaults.cardColors(containerColor = NeoDarkSurface),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = NeoCrimson,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            "Generando tu plan personalizado...",
                                            color = NeoTextPrimary,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    if (state.streamBuffer.isNotEmpty()) {
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            state.streamBuffer,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = NeoTextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    val plan = state.plan
                    if (plan != null) {
                        // Plan section cards
                        val sections = listOf(
                            "🥗 Nutrición" to plan.nutrition,
                            "😴 Sueño" to plan.sleep,
                            "🏃 Ejercicio" to plan.exercise,
                            "🧠 Salud Mental" to plan.mentalHealth
                        )
                        items(sections) { (title, items) ->
                            PlanSectionCard(
                                title = title,
                                items = items,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                            )
                        }
                    } else if (!state.isGenerating) {
                        // No plan yet
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp),
                                colors = CardDefaults.cardColors(containerColor = NeoDarkSurface),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(
                                    Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("📋", style = MaterialTheme.typography.displaySmall)
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "Aún no tienes un plan",
                                        color = NeoTextPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Genera tu plan personalizado con IA",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NeoTextSecondary
                                    )
                                }
                            }
                        }
                    }

                    if (state.error != null) {
                        item {
                            ErrorBanner(
                                state.error!!,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                            )
                        }
                    }

                    // Generate button when no plan
                    if (state.plan == null) {
                        item {
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = vm::generatePlan,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp)
                                    .height(52.dp),
                                enabled = !state.isGenerating,
                                colors = ButtonDefaults.buttonColors(containerColor = NeoCrimson),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(
                                    "Generar mi plan",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Crimson FAB when plan exists
            if (state.plan != null && !state.isLoading) {
                ExtendedFloatingActionButton(
                    onClick = vm::generatePlan,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 24.dp, bottom = 24.dp),
                    containerColor = NeoCrimson,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        "↺  Refrescar Plan",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanSectionCard(
    title: String,
    items: List<String>,
    modifier: Modifier = Modifier
) {
    val theme = sectionTheme(title)
    var expanded by remember { mutableStateOf(true) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(theme.gradient))
    ) {
        Column {
            // Top header area
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.25f))
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(
                            if (expanded) "▲" else "▼",
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }
            // Items list
            AnimatedVisibility(visible = expanded) {
                Column(
                    Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items.forEach { item ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                "•  ",
                                color = Color.White.copy(alpha = 0.7f),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                item,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
