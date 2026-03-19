package com.neovita.app.screens.results

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
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

class ResultsViewModel(private val assessmentRepo: AssessmentRepository) : ScreenModel {
    private val _state = MutableStateFlow(ResultsState())
    val state = _state.asStateFlow()

    init {
        screenModelScope.launch {
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
