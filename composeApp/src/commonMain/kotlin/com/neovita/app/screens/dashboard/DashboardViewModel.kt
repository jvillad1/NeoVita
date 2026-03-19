package com.neovita.app.screens.dashboard

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.neovita.shared.domain.model.LongevityPlan
import com.neovita.shared.domain.repository.PlanRepository
import com.neovita.shared.domain.repository.UserRepository
import com.neovita.shared.network.dto.UserDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DashboardState(
    val user: UserDto? = null,
    val plan: LongevityPlan? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

class DashboardViewModel(
    private val userRepo: UserRepository,
    private val planRepo: PlanRepository
) : ScreenModel {
    private val _state = MutableStateFlow(DashboardState())
    val state = _state.asStateFlow()

    init { load() }

    private fun load() {
        screenModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val user = userRepo.getMe().getOrNull()
            val plan = planRepo.getCurrent().getOrNull()
            _state.update { it.copy(user = user, plan = plan, isLoading = false) }
        }
    }
}
