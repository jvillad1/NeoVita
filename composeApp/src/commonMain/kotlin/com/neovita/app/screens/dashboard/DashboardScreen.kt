package com.neovita.app.screens.dashboard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import coil3.compose.AsyncImage
import org.koin.compose.koinInject
import com.neovita.app.ui.components.ScoreRing
import com.neovita.app.ui.theme.*

// ── Experience card data ──────────────────────────────────────────────────────

private data class ExpCard(
    val title: String,
    val subtitle: String,
    val imageUrl: String,
    val fallbackColors: List<Color>,
    val rating: Float,
    val price: String
)

private val EXP_CARDS = listOf(
    ExpCard(
        "Yoga al amanecer", "Tulum, México",
        "https://images.unsplash.com/photo-1506126613408-eca07ce68773?w=300&q=80",
        listOf(Color(0xFF3A1A5C), Color(0xFF7B3A10), Color(0xFFE8621A)), 4.9f, "Desde \$25 USD"
    ),
    ExpCard(
        "Dieta Mediterránea", "Clases en línea",
        "https://images.unsplash.com/photo-1512621776951-a57141f2eefd?w=300&q=80",
        listOf(Color(0xFF1A4A20), Color(0xFF2D7A30), Color(0xFF5AAF40)), 4.9f, "Desde \$25 USD"
    ),
    ExpCard(
        "Senderismo Grupal", "Valle de Antón, Panamá",
        "https://images.unsplash.com/photo-1551632811-561732d1e306?w=300&q=80",
        listOf(Color(0xFF0A3A20), Color(0xFF1A6040), Color(0xFF2A8050)), 4.8f, "Desde \$25 USD"
    ),
    ExpCard(
        "Meditación Guiada", "En línea",
        "https://images.unsplash.com/photo-1506905925346-21bda4d32df4?w=300&q=80",
        listOf(Color(0xFF1A0A3C), Color(0xFF3A1A6C), Color(0xFF5A2A8C)), 4.9f, "Desde \$25 USD"
    ),
    ExpCard(
        "Natación al aire libre", "Medellín, Colombia",
        "https://images.unsplash.com/photo-1571019613454-1cb2f99b2d8b?w=300&q=80",
        listOf(Color(0xFF0A2A4A), Color(0xFF0A4A7A), Color(0xFF0A6AAA)), 4.7f, "Desde \$25 USD"
    ),
)

// ── Screen ────────────────────────────────────────────────────────────────────

class DashboardScreen : Screen {
    @Composable
    override fun Content() {
        val vm: DashboardViewModel = koinInject()
        val state by vm.state.collectAsState()

        var show by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { show = true }

        // Dot-pattern background
        Box(Modifier.fillMaxSize().background(NeoDarkBg)) {
            DotPatternOverlay()

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {

                // ── Greeting ─────────────────────────────────────────────────
                item {
                    AnimatedVisibility(
                        visible = show,
                        enter = fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 3 }
                    ) {
                        Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 28.dp, bottom = 6.dp)) {
                            Text(
                                "Hola, Juan Guillermo",
                                color = NeoTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 32.sp,
                                lineHeight = 36.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Tu viaje hacia la longevidad comienza hoy.",
                                color = NeoTextSecondary,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                // ── Score card ───────────────────────────────────────────────
                item {
                    AnimatedVisibility(
                        visible = show,
                        enter = fadeIn(tween(450, delayMillis = 100)) + slideInVertically(tween(450, delayMillis = 100)) { it / 3 }
                    ) {
                        ScoreCard(state, Modifier.padding(horizontal = 22.dp, vertical = 14.dp))
                    }
                }

                // ── Experiences section ───────────────────────────────────────
                item {
                    AnimatedVisibility(
                        visible = show,
                        enter = fadeIn(tween(450, delayMillis = 200)) + slideInVertically(tween(450, delayMillis = 200)) { it / 3 }
                    ) {
                        Column {
                            // Section header
                            Row(
                                Modifier.fillMaxWidth()
                                    .padding(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Experiencias Recomendadas",
                                    color = NeoTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                                Text(
                                    "Ver todo",
                                    color = NeoCrimson,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }

                            // Horizontal cards
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 22.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(EXP_CARDS) { card -> ExpCardItem(card) }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Dot-pattern background overlay ───────────────────────────────────────────

@Composable
private fun DotPatternOverlay() {
    Canvas(Modifier.fillMaxSize()) {
        val dotRadius = 1.5f
        val spacing = 24.dp.toPx()
        val dotColor = Color.Black.copy(alpha = 0.06f)
        val cols = (size.width / spacing).toInt() + 2
        val rows = (size.height / spacing).toInt() + 2
        for (row in 0..rows) {
            for (col in 0..cols) {
                drawCircle(
                    color = dotColor,
                    radius = dotRadius,
                    center = Offset(col * spacing, row * spacing)
                )
            }
        }
    }
}

// ── Score card ────────────────────────────────────────────────────────────────

@Composable
private fun ScoreCard(state: DashboardState, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        val overall  = state.plan?.scores?.overall   ?: 85
        val nutricion = state.plan?.scores?.nutrition ?: 88
        val actividad = state.plan?.scores?.exercise  ?: 82
        val sueno    = state.plan?.scores?.sleep      ?: 85
        val bienestar = 84

        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 20.dp)) {

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Longevity Score", color = NeoTextMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text("❤️", fontSize = 18.sp)
            }

            Spacer(Modifier.height(14.dp))

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ScoreRing(score = overall, size = 120.dp, textColor = NeoCrimson)
            }

            Spacer(Modifier.height(10.dp))

            Text(
                "Tu puntuación esta semana",
                color = NeoTextMuted,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth()) {
                PillarStat("Nutrición", nutricion, NeoCrimson, Modifier.weight(1f))
                PillarStat("Actividad", actividad, NeoCrimson, Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                PillarStat("Sueño", sueno, NeoTextSecondary, Modifier.weight(1f))
                PillarStat("Bienestar", bienestar, NeoTextSecondary, Modifier.weight(1f))
            }

            Spacer(Modifier.height(14.dp))

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Ver detalles", color = NeoCrimson, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun PillarStat(label: String, score: Int, dotColor: Color, modifier: Modifier = Modifier) {
    Row(
        modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(7.dp).clip(CircleShape).background(dotColor)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            "$label: $score%",
            color = NeoTextMuted,
            fontSize = 11.sp
        )
    }
}

// ── Experience card ───────────────────────────────────────────────────────────

@Composable
private fun ExpCardItem(card: ExpCard) {
    Column(Modifier.width(130.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(155.dp)
                .clip(RoundedCornerShape(12.dp))
        ) {
            // Gradient fallback / placeholder (bottom layer)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(card.fallbackColors)
                )
            )

            // Real image on top — covers gradient once loaded
            AsyncImage(
                model = card.imageUrl,
                contentDescription = card.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Bottom scrim for text readability
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)),
                        startY = 55f
                    )
                )
            )
            // Title at bottom
            Text(
                card.title,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 15.sp,
                maxLines = 2
            )
        }

        Spacer(Modifier.height(6.dp))

        Text(card.subtitle, color = NeoTextSecondary, fontSize = 10.sp, maxLines = 1)

        Spacer(Modifier.height(3.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⭐", fontSize = 9.sp)
            Spacer(Modifier.width(2.dp))
            Text(
                "${card.rating} (124) · ${card.price}",
                color = NeoTextSecondary,
                fontSize = 10.sp,
                maxLines = 1
            )
        }
    }
}
