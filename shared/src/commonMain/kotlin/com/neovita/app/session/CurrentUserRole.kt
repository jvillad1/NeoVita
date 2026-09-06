package com.neovita.app.session

import com.neovita.shared.domain.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Rol del usuario, pedido UNA vez por sesión.
 *
 * La barra de navegación necesita saber si mostrar "Empresa", pero se recompone en cada
 * cambio de pestaña: preguntarlo ahí disparaba un `/api/users/me` extra cada vez, visible
 * en la traza de red como una llamada duplicada por cambio de pestaña.
 *
 * El rol de una sesión no cambia mientras dura, así que cachearlo es correcto, no un atajo.
 * `SessionManager.clear()` lo olvida para que la siguiente sesión no herede el anterior.
 */
object CurrentUserRole {
    private val _role = MutableStateFlow<String?>(null)
    val role: StateFlow<String?> = _role.asStateFlow()

    // Independiente de `role`: EMPLOYER es "puede ver el equipo de mi empresa", esto es
    // "puede administrar el contenido y las pantallas de TODA la app". Antes eran la misma
    // bandera, así que cualquier EMPLOYER de cualquier empresa podía reescribir lo que ve
    // todo el mundo — ver Authorization.kt#requireContentAdmin en el servidor.
    private val _isContentAdmin = MutableStateFlow(false)
    val isContentAdmin: StateFlow<Boolean> = _isContentAdmin.asStateFlow()

    val isEmployer: Boolean get() = _role.value == "EMPLOYER"

    private var pedido = false

    suspend fun ensureLoaded(userRepo: UserRepository) {
        if (pedido) return
        pedido = true
        val me = userRepo.getMe().getOrNull()
        _role.value = me?.role
        _isContentAdmin.value = me?.isContentAdmin ?: false
    }

    /** Al cerrar sesión: los permisos del siguiente usuario no tienen por qué ser los mismos. */
    fun clear() {
        pedido = false
        _role.value = null
        _isContentAdmin.value = false
    }
}
