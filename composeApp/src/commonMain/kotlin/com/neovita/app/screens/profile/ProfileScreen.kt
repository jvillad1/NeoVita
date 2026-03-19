package com.neovita.app.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.neovita.app.screens.login.LoginScreen
import com.neovita.app.screens.assessment.AssessmentScreen
import com.neovita.app.ui.theme.*
import com.neovita.shared.domain.repository.UserRepository
import com.neovita.shared.network.dto.UserDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileState(val user: UserDto? = null, val isLoading: Boolean = true)

class ProfileViewModel(private val userRepo: UserRepository) : ScreenModel {
    private val _state = MutableStateFlow(ProfileState())
    val state = _state.asStateFlow()
    init {
        screenModelScope.launch {
            val user = userRepo.getMe().getOrNull()
            _state.update { it.copy(user = user, isLoading = false) }
        }
    }
}

class ProfileScreen : Screen {
    @Composable override fun Content() {
        val vm = koinScreenModel<ProfileViewModel>()
        val state by vm.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow

        Column(
            Modifier.fillMaxSize().background(NeoBg)
                .verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Avatar
            Box(
                Modifier.size(80.dp).clip(CircleShape).background(NeoTeal200),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    state.user?.name?.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.headlineLarge,
                    color = NeoTeal900, fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                state.user?.name ?: "...",
                style = MaterialTheme.typography.titleLarge,
                color = NeoNavy, fontWeight = FontWeight.Bold
            )
            Text(
                state.user?.email ?: "",
                style = MaterialTheme.typography.bodyMedium, color = Color.Gray
            )
            state.user?.age?.let { age ->
                if (age > 0) Text("$age años", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Spacer(Modifier.height(32.dp))

            OutlinedButton(
                onClick = { navigator.push(AssessmentScreen()) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Realizar nueva evaluación", color = NeoTeal700)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { navigator.replaceAll(LoginScreen()) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeoRed)
            ) {
                Text("Cerrar sesión")
            }
        }
    }
}
