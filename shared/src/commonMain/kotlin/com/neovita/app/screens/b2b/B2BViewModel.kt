package com.neovita.app.screens.b2b

import com.neovita.shared.domain.repository.TeamRepository
import com.neovita.shared.network.dto.TeamResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class B2BState(
    val team: TeamResponse = TeamResponse(),
    val isLoading: Boolean = true,
    val error: String? = null
) {
    /** Miembros que todavía no han respondido la evaluación: no cuentan para el promedio. */
    val sinEvaluar: Int get() = team.totalMembers - team.evaluatedCount

    /** El servidor no manda promedios hasta que responda un mínimo de personas. */
    val faltanEvaluaciones: Boolean get() = team.averages == null

    val evaluacionesQueFaltan: Int get() = (team.minGroupSize - team.evaluatedCount).coerceAtLeast(0)
}

class B2BViewModel(private val teamRepo: TeamRepository) {
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val _state = MutableStateFlow(B2BState())
    val state = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        scope.launch {
            teamRepo.getTeam()
                .onSuccess { r ->
                    _state.update {
                        it.copy(team = r, isLoading = false)
                    }
                }
                .onFailure {
                    // Sin distinguir el motivo no se puede ayudar: un 403 significa que a esta
                    // cuenta no le asignaron empresa, y eso se arregla en la base, no reintentando.
                    val esPermiso = it.message?.contains("403") == true
                    _state.update { s ->
                        s.copy(
                            isLoading = false,
                            error = if (esPermiso) "Esta cuenta no tiene una empresa asignada"
                                    else "No pudimos cargar tu equipo. Intenta de nuevo."
                        )
                    }
                }
        }
    }
}
