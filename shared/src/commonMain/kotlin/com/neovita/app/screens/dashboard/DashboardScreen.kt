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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import coil3.compose.AsyncImage
import org.koin.compose.koinInject
import com.neovita.app.navigation.tabs.ChatTab
import com.neovita.app.navigation.tabs.HomeTab
import com.neovita.app.navigation.tabs.PlanTab
import com.neovita.app.navigation.tabs.ProfileTab
import com.neovita.app.screens.web.WebContentScreen
import com.neovita.app.ui.components.ScoreRing
import com.neovita.app.ui.sdui.SduiRenderer
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

// ── Blue Zones habits card data ───────────────────────────────────────────────

private val HABIT_CARDS = listOf(
    ExpCard(
        "Meditación Diaria", "5-20 min · Reduce cortisol",
        "https://images.unsplash.com/photo-1544367567-0f2fcb009e0b?w=300&q=80",
        listOf(Color(0xFF1A0A3C), Color(0xFF3A1060), Color(0xFF5C1A8C)), 4.9f, "Gratis"
    ),
    ExpCard(
        "Dieta Plant-Based", "Zonas Azules · Evidencia científica",
        "https://images.unsplash.com/photo-1490645935967-10de6ba17061?w=300&q=80",
        listOf(Color(0xFF0A2A0A), Color(0xFF1A5A20), Color(0xFF2A8A30)), 4.8f, "Plan incluido"
    ),
    ExpCard(
        "Caminar 10K Pasos", "Movimiento natural · Cada día",
        "https://images.unsplash.com/photo-1476480862126-209bfaa8edc8?w=300&q=80",
        listOf(Color(0xFF0A1A3A), Color(0xFF1A3A7A), Color(0xFF2A5AAA)), 4.7f, "Sin costo"
    ),
    ExpCard(
        "Sueño 8 Horas", "Ciclos REM · Recuperación celular",
        "https://images.unsplash.com/photo-1541781774459-bb2af2f05b55?w=300&q=80",
        listOf(Color(0xFF1A1A3C), Color(0xFF2A2A6C), Color(0xFF3A3A9C)), 4.9f, "Guía incluida"
    ),
    ExpCard(
        "Vínculos Sociales", "Tribu · Propósito compartido",
        "https://images.unsplash.com/photo-1529156069898-49953e39b3ac?w=300&q=80",
        listOf(Color(0xFF3A0A1A), Color(0xFF6A1A2A), Color(0xFF8B1042)), 4.8f, "Comunidad"
    ),
)

// ── Advanced longevity practices card data ────────────────────────────────────

private val PRACTICE_CARDS = listOf(
    ExpCard(
        "Terapia de Frío", "Wim Hof · Inmunidad y energía",
        "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=300&q=80",
        listOf(Color(0xFF0A2A3A), Color(0xFF0A4A6A), Color(0xFF0A6A9A)), 4.8f, "Técnica libre"
    ),
    ExpCard(
        "Ayuno 16/8", "Autofagia · Longevidad celular",
        "https://images.unsplash.com/photo-1498837167922-ddd27525d352?w=300&q=80",
        listOf(Color(0xFF2A1A0A), Color(0xFF5A3A0A), Color(0xFF8A5A0A)), 4.7f, "Plan gratis"
    ),
    ExpCard(
        "Sauna Infrarrojo", "Detox · Cardio pasivo · Piel",
        "https://images.unsplash.com/photo-1545167622-3a6ac756afa4?w=300&q=80",
        listOf(Color(0xFF3A0A0A), Color(0xFF6A1A0A), Color(0xFF8B1042)), 4.9f, "Desde \$15 USD"
    ),
    ExpCard(
        "Respiración 4-7-8", "Sistema nervioso · Sueño profundo",
        "https://images.unsplash.com/photo-1506905925346-21bda4d32df4?w=300&q=80",
        listOf(Color(0xFF0A1A2A), Color(0xFF0A3A5A), Color(0xFF0A5A8A)), 4.8f, "Técnica libre"
    ),
    ExpCard(
        "Entrenamiento Funcional", "Fuerza · Movilidad · Equilibrio",
        "https://images.unsplash.com/photo-1517836357463-d25dfeac3438?w=300&q=80",
        listOf(Color(0xFF1A1A1A), Color(0xFF3A3A3A), Color(0xFF5A2A2A)), 4.7f, "Desde \$10 USD"
    ),
)

// ── Screen ────────────────────────────────────────────────────────────────────

class DashboardScreen : Screen {
    @Composable
    override fun Content() {
        val vm: DashboardViewModel = koinInject()
        val state by vm.state.collectAsState()
        val tabNavigator = LocalTabNavigator.current
        val navigator = LocalNavigator.currentOrThrow
        val uriHandler = LocalUriHandler.current

        val screenDef = state.screenDef
        if (screenDef != null) {
            Column(Modifier.fillMaxSize().background(NeoDarkBg)) {
                SduiGreetingHeader()
                Box(Modifier.weight(1f)) {
                    SduiRenderer(
                        definition = screenDef,
                        scores = state.plan?.scores,
                        feed = state.feed,
                        onNavigateTab = { target ->
                            tabNavigator.current = when (target) {
                                "home" -> HomeTab
                                "chat" -> ChatTab
                                "plan" -> PlanTab
                                "profile" -> ProfileTab
                                else -> tabNavigator.current
                            }
                        },
                        onOpenUrl = { url -> uriHandler.openUri(url) },
                        onOpenWebView = { title, url ->
                            navigator.parent?.push(WebContentScreen(title, url))
                        },
                    )
                }
            }
        } else {
            DashboardFallback(state)
        }
    }
}

// ── Greeting header for the SDUI path — NOT shared with DashboardFallback (that
//    composable is the insurance policy and stays untouched). Same visual reference
//    (text, typography, spacing, entrance animation) as DashboardFallback's greeting
//    block, reimplemented locally so the SDUI branch keeps its own chrome. ───────────

@Composable
private fun SduiGreetingHeader() {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { show = true }

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

// ── Fallback — the ORIGINAL screen body, renamed verbatim. This is the insurance
//    policy for when there is no server-provided screen definition (no cache, first
//    launch offline, decode failure, etc.) — do NOT refactor it, and do NOT share new
//    SDUI composables (SduiCard/SduiRenderer) with it. ─────────────────────────────

@Composable
private fun DashboardFallback(state: DashboardState) {
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
                        CardSection("Experiencias Recomendadas", EXP_CARDS)
                    }
                }

                // ── Blue Zones habits section ─────────────────────────────────
                item {
                    AnimatedVisibility(
                        visible = show,
                        enter = fadeIn(tween(450, delayMillis = 300)) + slideInVertically(tween(450, delayMillis = 300)) { it / 3 }
                    ) {
                        CardSection("Hábitos de Zonas Azules", HABIT_CARDS)
                    }
                }

                // ── Advanced practices section ────────────────────────────────
                item {
                    AnimatedVisibility(
                        visible = show,
                        enter = fadeIn(tween(450, delayMillis = 400)) + slideInVertically(tween(450, delayMillis = 400)) { it / 3 }
                    ) {
                        CardSection("Prácticas de Longevidad", PRACTICE_CARDS)
                    }
                }
            }
        }
}

// ── Reusable section with header + horizontal card row ────────────────────────

@Composable
private fun CardSection(title: String, cards: List<ExpCard>) {
    Column {
        Row(
            Modifier.fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = NeoTextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Ver todo", color = NeoCrimson, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(cards) { card -> ExpCardItem(card) }
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
