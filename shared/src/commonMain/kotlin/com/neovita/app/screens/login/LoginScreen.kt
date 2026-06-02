package com.neovita.app.screens.login

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import org.koin.compose.koinInject
import com.neovita.app.screens.main.MainScreen
import com.neovita.app.screens.onboarding.OnboardingScreen
import com.neovita.app.ui.components.ErrorBanner
import com.neovita.app.ui.theme.*

class LoginScreen : Screen {
    @Composable
    override fun Content() {
        val vm: LoginViewModel = koinInject()
        val state by vm.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow

        LaunchedEffect(state.success) {
            state.success?.let { auth ->
                navigator.replace(if (auth.isNewUser) OnboardingScreen() else MainScreen())
            }
        }

        // Entrance animation trigger
        var show by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { show = true }

        // Floating orbs animation
        val infiniteTransition = rememberInfiniteTransition(label = "orbs")
        val orbOffset1 by infiniteTransition.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Reverse),
            label = "orb1"
        )
        val orbOffset2 by infiniteTransition.animateFloat(
            initialValue = 1f, targetValue = 0f,
            animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Reverse),
            label = "orb2"
        )

        // Logo scale bounce
        val logoScale by animateFloatAsState(
            targetValue = if (show) 1f else 0.4f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "logoScale"
        )

        Box(Modifier.fillMaxSize()) {

            // ── Hero background (top ~58%) ─────────────────────────────────
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.60f)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF5C0B2C),
                                Color(0xFF8B1042),
                                Color(0xFFAA1A55)
                            )
                        )
                    )
            ) {
                // Decorative floating orbs
                Canvas(Modifier.fillMaxSize()) {
                    drawOrbs(orbOffset1, orbOffset2)
                }

                // Dot grid overlay
                Canvas(Modifier.fillMaxSize()) {
                    val dotR = 1.2f
                    val spacing = 28.dp.toPx()
                    val dotColor = Color.White.copy(alpha = 0.08f)
                    val cols = (size.width / spacing).toInt() + 2
                    val rows = (size.height / spacing).toInt() + 2
                    for (r in 0..rows) for (c in 0..cols) {
                        drawCircle(dotColor, dotR, Offset(c * spacing, r * spacing))
                    }
                }

                // NeoVita logo + name centered in hero
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Logo icon
                    Box(
                        Modifier
                            .scale(logoScale)
                            .size(88.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.White.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color.White.copy(alpha = 0.95f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🌿", fontSize = 36.sp)
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    AnimatedVisibility(
                        visible = show,
                        enter = fadeIn(tween(600, delayMillis = 200)) +
                                slideInVertically(tween(600, delayMillis = 200)) { it / 2 }
                    ) {
                        Text(
                            "NeoVita",
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = (-0.5).sp
                        )
                    }

                    AnimatedVisibility(
                        visible = show,
                        enter = fadeIn(tween(600, delayMillis = 350))
                    ) {
                        Text(
                            "Longevity · AI · Wellness",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.75f),
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // ── Bottom card sliding up ─────────────────────────────────────
            AnimatedVisibility(
                visible = show,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) { it / 2 } + fadeIn(tween(500, delayMillis = 100))
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                    color = Color.White,
                    shadowElevation = 24.dp
                ) {
                    Column(
                        Modifier.padding(start = 32.dp, end = 32.dp, top = 36.dp, bottom = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {

                        // Heading
                        Text(
                            "Tu compañero de\nlongevidad",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeoTextPrimary,
                            textAlign = TextAlign.Center,
                            lineHeight = 32.sp
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            "Coaching integral con IA personalizada\npara vivir más y mejor",
                            fontSize = 14.sp,
                            color = NeoTextSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )

                        Spacer(Modifier.height(28.dp))

                        // Feature pills
                        val features = listOf(
                            "🧬" to "Plan personalizado con IA",
                            "💬" to "Coach de bienestar 24/7",
                            "📊" to "Seguimiento de longevidad"
                        )
                        features.forEachIndexed { i, (icon, label) ->
                            AnimatedVisibility(
                                visible = show,
                                enter = fadeIn(tween(400, delayMillis = 500 + i * 100)) +
                                        slideInHorizontally(tween(400, delayMillis = 500 + i * 100)) { -it / 3 }
                            ) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        Modifier
                                            .size(36.dp)
                                            .background(NeoCrimson.copy(alpha = 0.09f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(icon, fontSize = 16.sp)
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        label,
                                        fontSize = 14.sp,
                                        color = NeoTextPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(32.dp))

                        // CTA Button
                        AnimatedVisibility(
                            visible = show,
                            enter = fadeIn(tween(500, delayMillis = 800)) +
                                    slideInVertically(tween(500, delayMillis = 800)) { it / 2 }
                        ) {
                            Column(
                                Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (state.isLoading) {
                                    CircularProgressIndicator(
                                        color = NeoCrimson,
                                        modifier = Modifier.size(40.dp)
                                    )
                                } else {
                                    Button(
                                        onClick = vm::signInWithGoogle,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(56.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = NeoCrimson),
                                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                                    ) {
                                        Text(
                                            "Continuar con Google",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White
                                        )
                                    }

                                    Spacer(Modifier.height(14.dp))

                                    TextButton(onClick = { navigator.replace(MainScreen()) }) {
                                        Text(
                                            "Ahora no",
                                            color = NeoTextSecondary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Normal
                                        )
                                    }
                                }

                                if (state.error != null) {
                                    Spacer(Modifier.height(8.dp))
                                    ErrorBanner(state.error!!)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Decorative orb drawing ─────────────────────────────────────────────────

private fun DrawScope.drawOrbs(t1: Float, t2: Float) {
    // Large soft orb top-right, drifting slowly
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.18f), Color.Transparent),
            center = Offset(size.width * (0.75f + t1 * 0.1f), size.height * (0.15f + t1 * 0.08f)),
            radius = size.width * 0.55f
        ),
        radius = size.width * 0.55f,
        center = Offset(size.width * (0.75f + t1 * 0.1f), size.height * (0.15f + t1 * 0.08f))
    )
    // Medium orb bottom-left
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0xFFFFB0C8).copy(alpha = 0.25f), Color.Transparent),
            center = Offset(size.width * (0.15f + t2 * 0.1f), size.height * (0.75f + t2 * 0.08f)),
            radius = size.width * 0.40f
        ),
        radius = size.width * 0.40f,
        center = Offset(size.width * (0.15f + t2 * 0.1f), size.height * (0.75f + t2 * 0.08f))
    )
    // Small accent orb center
    drawCircle(
        color = Color(0xFFE0698A).copy(alpha = 0.20f),
        radius = size.width * 0.20f,
        center = Offset(size.width * (0.45f + t1 * 0.05f), size.height * (0.50f - t2 * 0.05f))
    )
}
