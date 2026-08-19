package com.neovita.app.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import com.neovita.app.health.HealthSyncClient
import com.neovita.app.health.HealthSyncState
import com.neovita.app.screens.login.LoginScreen
import com.neovita.app.screens.admin.ContentAdminScreen
import com.neovita.app.screens.assessment.AssessmentScreen
import com.neovita.app.screens.web.WebContentScreen
import com.neovita.app.ui.assets.profileAvatarModel
import com.neovita.app.ui.web.supportsAuthenticatedWebView
import com.neovita.app.ui.theme.*
import com.neovita.shared.config.RemoteConfigRepository
import com.neovita.shared.config.isFeatureEnabled
import com.neovita.shared.data.cache.LocalCache
import com.neovita.shared.domain.repository.UserRepository
import com.neovita.shared.network.ApiService
import com.neovita.shared.network.dto.UserDto
import com.neovita.shared.session.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.koin.compose.koinInject

enum class MetricField { STEPS, WEIGHT, BP_SYS, BP_DIA, GLUCOSE }

data class MetricsState(
    val steps: String = "",
    val weightKg: String = "",
    val bloodPressureSys: String = "",
    val bloodPressureDia: String = "",
    val glucoseMgdl: String = "",
    val saved: Boolean = false
)

data class ProfileState(
    val user: UserDto? = null,
    val isLoading: Boolean = true,
    val metrics: MetricsState = MetricsState()
)

class ProfileViewModel(
    private val userRepo: UserRepository,
    private val cache: LocalCache?
) {
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val _state = MutableStateFlow(ProfileState())
    val state = _state.asStateFlow()

    init {
        scope.launch {
            val user = userRepo.getMe().getOrNull()
            val metrics = try {
                val saved = cache?.getMetrics(user?.id ?: "")
                if (saved != null) MetricsState(
                    steps = saved.steps?.toString() ?: "",
                    weightKg = saved.weightKg?.toString() ?: "",
                    bloodPressureSys = saved.bloodPressureSys?.toString() ?: "",
                    bloodPressureDia = saved.bloodPressureDia?.toString() ?: "",
                    glucoseMgdl = saved.glucoseMgdl?.toString() ?: ""
                ) else MetricsState()
            } catch (_: Exception) {
                MetricsState()
            }
            _state.update { it.copy(user = user, isLoading = false, metrics = metrics) }
        }
    }

    fun updateMetric(field: MetricField, value: String) {
        _state.update { s ->
            s.copy(metrics = when (field) {
                MetricField.STEPS    -> s.metrics.copy(steps = value, saved = false)
                MetricField.WEIGHT   -> s.metrics.copy(weightKg = value, saved = false)
                MetricField.BP_SYS   -> s.metrics.copy(bloodPressureSys = value, saved = false)
                MetricField.BP_DIA   -> s.metrics.copy(bloodPressureDia = value, saved = false)
                MetricField.GLUCOSE  -> s.metrics.copy(glucoseMgdl = value, saved = false)
            })
        }
    }

    fun saveMetrics() {
        val userId = _state.value.user?.id ?: return
        val m = _state.value.metrics
        scope.launch {
            try {
                cache?.upsertMetrics(
                    userId = userId,
                    steps = m.steps.toLongOrNull(),
                    weightKg = m.weightKg.toDoubleOrNull(),
                    bloodPressureSys = m.bloodPressureSys.toLongOrNull(),
                    bloodPressureDia = m.bloodPressureDia.toLongOrNull(),
                    glucoseMgdl = m.glucoseMgdl.toLongOrNull(),
                )
            } catch (_: Exception) { /* table may not exist yet on old DB */ }
            _state.update { it.copy(metrics = it.metrics.copy(saved = true)) }
        }
    }
}

class ProfileScreen : Screen {
    @Composable
    override fun Content() {
        val vm: ProfileViewModel = koinInject()
        val state by vm.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow

        Column(
            Modifier
                .fillMaxSize()
                .background(NeoDarkBg)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // Avatar + Edit Profile section
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar circle with crimson border
                if (profileAvatarModel != null) {
                    AsyncImage(
                        model = profileAvatarModel,
                        contentDescription = "Profile picture",
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .border(3.dp, NeoCrimson, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .border(3.dp, NeoCrimson, CircleShape)
                            .background(NeoCrimson.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        val initial = state.user?.name?.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                        Text(
                            initial,
                            style = MaterialTheme.typography.headlineLarge,
                            color = NeoCrimson,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.width(20.dp))
                // Edit Profile pill button
                Surface(
                    onClick = {},
                    shape = RoundedCornerShape(24.dp),
                    color = NeoCrimson
                ) {
                    Text(
                        "Editar Perfil",
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Name, subtitle, member since
            Text(
                state.user?.name ?: "Tu perfil",
                style = MaterialTheme.typography.titleLarge,
                color = NeoTextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Longevity Explorer",
                style = MaterialTheme.typography.bodyMedium,
                color = NeoTextSecondary
            )
            Text(
                "Member since 2023",
                style = MaterialTheme.typography.bodySmall,
                color = NeoTextSecondary.copy(alpha = 0.7f)
            )

            Spacer(Modifier.height(28.dp))

            // Settings menu card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NeoDarkSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column {
                    SettingsItem(
                        icon = "❤️",
                        title = "Mis Datos de Salud",
                        onClick = {}
                    )
                    HorizontalDivider(color = NeoDarkSurface2)
                    SettingsItem(
                        icon = "📱",
                        title = "Dispositivos Conectados",
                        onClick = {}
                    )
                    HorizontalDivider(color = NeoDarkSurface2)
                    SettingsItem(
                        icon = "🍽️",
                        title = "Preferencias de Dieta",
                        onClick = {}
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Metrics card
            Text(
                "Mis métricas",
                style = MaterialTheme.typography.titleMedium,
                color = NeoTextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Registro manual",
                style = MaterialTheme.typography.bodySmall,
                color = NeoTextSecondary
            )
            Spacer(Modifier.height(10.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NeoDarkSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    MetricInput(
                        "👣 Pasos diarios", "ej. 8000",
                        state.metrics.steps, KeyboardType.Number
                    ) { vm.updateMetric(MetricField.STEPS, it) }
                    MetricInput(
                        "⚖️ Peso (kg)", "ej. 72.5",
                        state.metrics.weightKg, KeyboardType.Decimal
                    ) { vm.updateMetric(MetricField.WEIGHT, it) }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(Modifier.weight(1f)) {
                            MetricInput(
                                "🩺 Presión Sistólica", "ej. 120",
                                state.metrics.bloodPressureSys, KeyboardType.Number
                            ) { vm.updateMetric(MetricField.BP_SYS, it) }
                        }
                        Box(Modifier.weight(1f)) {
                            MetricInput(
                                "Diastólica", "ej. 80",
                                state.metrics.bloodPressureDia, KeyboardType.Number
                            ) { vm.updateMetric(MetricField.BP_DIA, it) }
                        }
                    }
                    MetricInput(
                        "🩸 Glucosa (mg/dL)", "ej. 95",
                        state.metrics.glucoseMgdl, KeyboardType.Number
                    ) { vm.updateMetric(MetricField.GLUCOSE, it) }
                }
            }

            Spacer(Modifier.height(10.dp))
            Button(
                onClick = vm::saveMetrics,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.metrics.saved) NeoCrimson.copy(alpha = 0.6f) else NeoCrimson
                )
            ) {
                Text(
                    if (state.metrics.saved) "✓ Guardado" else "Guardar métricas",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(24.dp))

            // Actions card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NeoDarkSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column {
                    SettingsItem(
                        icon = "📝",
                        title = "Realizar nueva evaluación",
                        onClick = { navigator.parent?.push(AssessmentScreen()) }
                    )
                    HorizontalDivider(color = NeoDarkSurface2)
                    // "healthSync" nace apagado: la entrada solo aparece cuando el servidor
                    // la enciende (ship dormant), y leer datos siempre lo inicia la usuaria.
                    val healthConfig by koinInject<RemoteConfigRepository>().config.collectAsState()
                    if (healthConfig.isFeatureEnabled("healthSync", default = false)) {
                        val healthApiService = koinInject<ApiService>()
                        val healthClient = remember { HealthSyncClient() }
                        val healthScope = rememberCoroutineScope()
                        var healthState by remember { mutableStateOf<HealthSyncState?>(null) }
                        val healthLabel = when (healthState) {
                            null -> "Conectar datos de salud"
                            HealthSyncState.SYNCING -> "Sincronizando…"
                            HealthSyncState.SYNCED -> "Datos de salud sincronizados"
                            HealthSyncState.NEEDS_PERMISSION -> "Permiso de salud pendiente"
                            HealthSyncState.UNAVAILABLE -> "Health Connect no disponible"
                            HealthSyncState.ERROR -> "No se pudo sincronizar — reintentar"
                        }
                        SettingsItem(
                            icon = "❤️",
                            title = healthLabel,
                            onClick = {
                                if (healthState == HealthSyncState.SYNCING) return@SettingsItem
                                healthScope.launch {
                                    healthState = HealthSyncState.SYNCING
                                    healthState = if (!healthClient.isAvailable()) {
                                        HealthSyncState.UNAVAILABLE
                                    } else if (!healthClient.requestPermissions()) {
                                        HealthSyncState.NEEDS_PERMISSION
                                    } else {
                                        healthClient.sync(healthApiService)
                                    }
                                }
                            }
                        )
                        HorizontalDivider(color = NeoDarkSurface2)
                    }
                    // Content administration — only for EMPLOYER (admin) accounts.
                    if (state.user?.role == "EMPLOYER") {
                        SettingsItem(
                            icon = "🗂️",
                            title = "Administrar contenido",
                            onClick = { navigator.parent?.push(ContentAdminScreen()) }
                        )
                        HorizontalDivider(color = NeoDarkSurface2)
                        if (supportsAuthenticatedWebView()) {
                            SettingsItem(
                                icon = "🎛️",
                                title = "Editar pantallas",
                                onClick = {
                                    navigator.parent?.push(
                                        WebContentScreen(title = "Editar pantallas", url = "/web/admin/screens")
                                    )
                                }
                            )
                            HorizontalDivider(color = NeoDarkSurface2)
                        }
                    }
                    // Sign out item in red
                    ListItem(
                        headlineContent = {
                            Text(
                                "Cerrar sesión",
                                color = NeoRed,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        leadingContent = {
                            Text("🚪", style = MaterialTheme.typography.titleMedium)
                        },
                        trailingContent = {
                            Text(">", color = NeoRed)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Transparent),
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
            // Actual sign out action
            OutlinedButton(
                onClick = { SessionManager.clear(); navigator.parent?.replaceAll(LoginScreen()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeoRed),
                border = androidx.compose.foundation.BorderStroke(1.dp, NeoRed.copy(alpha = 0.5f))
            ) {
                Text("Cerrar sesión", fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsItem(icon: String, title: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color.Transparent
    ) {
        ListItem(
            headlineContent = {
                Text(title, color = NeoTextPrimary, fontWeight = FontWeight.Medium)
            },
            leadingContent = {
                Text(icon, style = MaterialTheme.typography.titleMedium)
            },
            trailingContent = {
                Text(">", color = NeoTextSecondary)
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

@Composable
private fun MetricInput(
    label: String,
    placeholder: String,
    value: String,
    keyboardType: KeyboardType,
    onValueChange: (String) -> Unit
) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = NeoTextPrimary,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    placeholder,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray
                )
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeoCrimson,
                unfocusedBorderColor = Color(0xFFDDDDE8)
            )
        )
    }
}
