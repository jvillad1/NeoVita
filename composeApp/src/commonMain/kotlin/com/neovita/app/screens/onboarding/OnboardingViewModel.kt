package com.neovita.app.screens.onboarding

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.neovita.shared.domain.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingState(
    val name: String = "", val age: String = "",
    val isLoading: Boolean = false, val error: String? = null,
    val done: Boolean = false
)

class OnboardingViewModel(private val userRepo: UserRepository) {
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val _state = MutableStateFlow(OnboardingState())
    val state = _state.asStateFlow()

    fun onNameChange(v: String) = _state.update { it.copy(name = v) }
    fun onAgeChange(v: String) = _state.update { it.copy(age = v) }

    fun save() {
        val age = _state.value.age.toIntOrNull()
        if (_state.value.name.isBlank() || age == null || age < 18) {
            _state.update { it.copy(error = "Ingresa un nombre y edad válida (mínimo 18 años)") }
            return
        }
        _state.update { it.copy(isLoading = true, error = null) }
        scope.launch {
            userRepo.updateMe(name = _state.value.name, age = age)
                .onSuccess { _state.update { it.copy(isLoading = false, done = true) } }
                .onFailure { _state.update { it.copy(isLoading = false, error = "Error al guardar") } }
        }
    }
}
