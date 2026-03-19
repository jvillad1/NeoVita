package com.neovita.app.screens.plan

import androidx.compose.animation.AnimatedVisibility
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
import com.neovita.app.ui.components.ErrorBanner
import com.neovita.app.ui.theme.*

class PlanScreen : Screen {
    @Composable override fun Content() {
        val vm = koinScreenModel<PlanViewModel>()
        val state by vm.state.collectAsState()

        Column(
            Modifier.fillMaxSize().background(NeoBg)
                .verticalScroll(rememberScrollState()).padding(24.dp)
        ) {
            Text(
                "Mi Plan de Longevidad",
                style = MaterialTheme.typography.headlineMedium,
                color = NeoNavy, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))

            if (state.isLoading) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NeoTeal700)
                }
            } else {
                if (state.isGenerating) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = NeoSurface)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = NeoTeal700, strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Generando tu plan personalizado...", color = NeoNavy)
                            }
                            if (state.streamBuffer.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    state.streamBuffer,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }

                val plan = state.plan
                if (plan != null) {
                    PlanSection("🥗 Nutrición", plan.nutrition)
                    PlanSection("😴 Sueño", plan.sleep)
                    PlanSection("🏃 Ejercicio", plan.exercise)
                } else if (!state.isGenerating) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = NeoSurface)
                    ) {
                        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Aún no tienes un plan", color = NeoNavy)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Genera tu plan personalizado con IA",
                                style = MaterialTheme.typography.bodySmall, color = Color.Gray
                            )
                        }
                    }
                }

                state.error?.let { ErrorBanner(it, modifier = Modifier.padding(vertical = 8.dp)) }

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = vm::generatePlan,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = !state.isGenerating,
                    colors = ButtonDefaults.buttonColors(containerColor = NeoTeal700)
                ) {
                    Text(
                        if (state.plan != null) "Regenerar plan" else "Generar mi plan",
                        color = Color.White, fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanSection(title: String, items: List<String>) {
    var expanded by remember { mutableStateOf(true) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = NeoSurface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge,
                    color = NeoNavy, fontWeight = FontWeight.Bold)
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "▲" else "▼", color = NeoTeal700)
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    items.forEach { item ->
                        Row(Modifier.padding(vertical = 4.dp)) {
                            Text("• ", color = NeoTeal700, fontWeight = FontWeight.Bold)
                            Text(item, style = MaterialTheme.typography.bodyMedium, color = NeoNavy)
                        }
                    }
                }
            }
        }
    }
}
