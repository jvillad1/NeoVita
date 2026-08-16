package com.neovita.app.screens.assessment

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.neovita.shared.domain.repository.AssessmentRepository
import com.neovita.shared.network.dto.AssessmentResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class Question(
    val id: String,
    val text: String,
    val options: List<String> = emptyList(),
    val type: String = "single"
)

val QUESTIONS = listOf(
    Question("exercise_frequency", "¿Cuántas veces a la semana haces ejercicio?",
        listOf("Todos los días", "4-5 veces", "2-3 veces", "1 vez", "Nunca")),
    Question("exercise_type", "¿Qué tipo de ejercicio haces principalmente?",
        listOf("Cardio (caminar, correr, ciclismo)", "Pesas o resistencia", "Yoga o pilates",
            "Deportes de equipo", "No hago ejercicio")),
    Question("sleep_hours", "¿Cuántas horas duermes por noche?",
        listOf("8+ horas", "7-8 horas", "6-7 horas", "5-6 horas", "Menos de 5 horas")),
    Question("sleep_quality", "¿Cómo calificarías la calidad de tu sueño? (1-10)", type = "slider"),
    Question("main_goal", "¿Cuál es tu principal objetivo de longevidad?",
        listOf("Aumentar energía y vitalidad", "Mejorar memoria y función cognitiva",
            "Reducir riesgo de enfermedades", "Bajar de peso saludablemente",
            "Manejar el estrés y bienestar mental"))
)

data class AssessmentState(
    val currentQuestion: Int = 0,
    val answers: Map<String, String> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val completed: AssessmentResponse? = null,
    /** La evaluación se guardó. Es la única señal válida para navegar a los resultados. */
    val saved: Boolean = false
)

class AssessmentViewModel(private val assessmentRepo: AssessmentRepository) {
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val _state = MutableStateFlow(AssessmentState())
    val state = _state.asStateFlow()

    fun answer(questionId: String, value: String) {
        val newAnswers = _state.value.answers + (questionId to value)
        val nextQuestion = _state.value.currentQuestion + 1
        if (nextQuestion >= QUESTIONS.size) {
            // La respuesta de la ÚLTIMA pregunta también tiene que entrar en el estado: antes
            // sólo lo hacía la rama del else, así que la pantalla se quedaba con cuatro y no
            // se enteraba nunca de que había terminado.
            //
            // answers e isLoading se escriben JUNTOS, en una sola actualización: si se
            // publicara el mapa completo con isLoading todavía en false, habría un instante
            // en el que la pantalla creería que ya acabó y navegaría antes de guardar.
            _state.update { it.copy(answers = newAnswers, isLoading = true, error = null) }
            submitAssessment(newAnswers)
        } else {
            _state.update { it.copy(answers = newAnswers, currentQuestion = nextQuestion) }
        }
    }

    /** Vuelve a la pregunta anterior conservando lo ya respondido. En la primera, no hace nada. */
    fun goBack() {
        if (_state.value.currentQuestion == 0) return
        _state.update { it.copy(currentQuestion = it.currentQuestion - 1, error = null) }
    }

    private fun submitAssessment(answers: Map<String, String>) {
        scope.launch {
            assessmentRepo.saveAssessment(
                exerciseFrequency = answers["exercise_frequency"] ?: "",
                exerciseType = answers["exercise_type"] ?: "",
                sleepHours = answers["sleep_hours"] ?: "",
                sleepQuality = answers["sleep_quality"]?.toIntOrNull() ?: 5,
                mainGoal = answers["main_goal"] ?: ""
            ).onSuccess {
                _state.update { it.copy(isLoading = false, saved = true) }
            }.onFailure {
                _state.update { it.copy(isLoading = false, error = "Error al guardar evaluación") }
            }
        }
    }

    /** Sólo cuando el guardado confirmó. Inferirlo del número de respuestas fue el bug. */
    val isDone: Boolean get() = _state.value.saved
}
