package com.neovita.app.screens.results

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.neovita.shared.domain.model.PillarScores
import com.neovita.shared.domain.repository.AssessmentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ResultsState(
    val scores: PillarScores? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

class ResultsViewModel(private val assessmentRepo: AssessmentRepository) {
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val _state = MutableStateFlow(ResultsState())
    val state = _state.asStateFlow()

    init {
        scope.launch {
            val assessment = assessmentRepo.getLatestAssessment("")
            _state.update {
                it.copy(
                    scores = assessment?.scores,
                    isLoading = false,
                    error = if (assessment == null) "No se encontró evaluación" else null
                )
            }
        }
    }
}
