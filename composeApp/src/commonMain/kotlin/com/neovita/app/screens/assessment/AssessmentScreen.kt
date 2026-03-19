package com.neovita.app.screens.assessment

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.neovita.app.screens.results.ResultsScreen
import com.neovita.app.ui.components.ErrorBanner
import com.neovita.app.ui.theme.*

class AssessmentScreen : Screen {
    @Composable override fun Content() {
        val vm = koinScreenModel<AssessmentViewModel>()
        val state by vm.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow

        LaunchedEffect(state.isLoading, state.answers.size) {
            if (!state.isLoading && state.answers.size >= QUESTIONS.size && state.error == null) {
                navigator.replace(ResultsScreen())
            }
        }

        val question = QUESTIONS.getOrNull(state.currentQuestion)

        Column(Modifier.fillMaxSize().background(NeoBg).padding(24.dp)) {
            // Progress bar
            LinearProgressIndicator(
                progress = { (state.currentQuestion.toFloat()) / QUESTIONS.size },
                modifier = Modifier.fillMaxWidth(),
                color = NeoTeal700,
                trackColor = NeoTeal200
            )
            Spacer(Modifier.height(24.dp))

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = NeoTeal700)
                        Spacer(Modifier.height(16.dp))
                        Text("Calculando tu perfil...", color = NeoNavy)
                    }
                }
            } else if (question != null) {
                Text(
                    "Pregunta ${state.currentQuestion + 1} de ${QUESTIONS.size}",
                    style = MaterialTheme.typography.labelSmall, color = Color.Gray
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    question.text,
                    style = MaterialTheme.typography.headlineMedium,
                    color = NeoNavy, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(32.dp))

                if (question.type == "slider") {
                    var sliderValue by remember { mutableStateOf(5f) }
                    Column {
                        Text(
                            "Calidad: ${sliderValue.toInt()}/10",
                            style = MaterialTheme.typography.titleLarge,
                            color = NeoTeal700, fontWeight = FontWeight.Bold
                        )
                        Slider(
                            value = sliderValue,
                            onValueChange = { sliderValue = it },
                            valueRange = 1f..10f,
                            steps = 8,
                            colors = SliderDefaults.colors(thumbColor = NeoTeal700, activeTrackColor = NeoTeal700)
                        )
                        Button(
                            onClick = { vm.answer(question.id, sliderValue.toInt().toString()) },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeoTeal700)
                        ) {
                            Text("Continuar →", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                } else {
                    question.options.forEach { option ->
                        OutlinedButton(
                            onClick = { vm.answer(question.id, option) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeoNavy)
                        ) {
                            Text(option, modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }

                state.error?.let { ErrorBanner(it, modifier = Modifier.padding(top = 16.dp)) }
            }
        }
    }
}
