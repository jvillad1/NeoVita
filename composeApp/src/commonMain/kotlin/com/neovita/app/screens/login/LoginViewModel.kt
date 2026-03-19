package com.neovita.app.screens.login

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.neovita.app.auth.GoogleSignInClient
import com.neovita.shared.network.ApiService
import com.neovita.shared.network.dto.AuthResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginState(
    val isLoading: Boolean = false,
    val success: AuthResponse? = null,
    val error: String? = null
)

class LoginViewModel(
    private val apiService: ApiService,
    private val googleSignInClient: GoogleSignInClient
) : ScreenModel {
    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()

    fun signInWithGoogle() {
        _state.update { it.copy(isLoading = true, error = null) }
        screenModelScope.launch {
            val result = googleSignInClient.signIn()
            if (result.idToken == null) {
                _state.update { it.copy(isLoading = false, error = result.error ?: "Error al iniciar sesión") }
                return@launch
            }
            apiService.authenticateWithGoogle(result.idToken)
                .onSuccess { auth ->
                    apiService.setToken(auth.token)
                    _state.update { it.copy(isLoading = false, success = auth) }
                }
                .onFailure {
                    _state.update { it.copy(isLoading = false, error = "Error de conexión") }
                }
        }
    }
}
