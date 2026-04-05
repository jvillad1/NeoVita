package com.neovita.app.screens.plan

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.neovita.shared.domain.model.LongevityPlan
import com.neovita.shared.domain.repository.PlanRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlanState(
    val plan: LongevityPlan? = null,
    val isLoading: Boolean = false,
    val isGenerating: Boolean = false,
    val streamBuffer: String = "",
    val error: String? = null
)

class PlanViewModel(private val planRepo: PlanRepository) {
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val _state = MutableStateFlow(PlanState())
    val state = _state.asStateFlow()

    init { load() }

    private fun load() {
        scope.launch {
            _state.update { it.copy(isLoading = true) }
            planRepo.getCurrent()
                .onSuccess { plan -> _state.update { it.copy(plan = plan, isLoading = false) } }
                .onFailure { _state.update { it.copy(isLoading = false) } }
        }
    }

    fun generatePlan() {
        _state.update { it.copy(isGenerating = true, streamBuffer = "", error = null) }
        scope.launch {
            planRepo.streamGenerate()
                .catch { _state.update { it.copy(isGenerating = false, error = "Error al generar plan") } }
                .collect { chunk ->
                    _state.update { it.copy(streamBuffer = it.streamBuffer + chunk) }
                }
            _state.update { it.copy(isGenerating = false) }
            load()  // Reload persisted plan
        }
    }
}
