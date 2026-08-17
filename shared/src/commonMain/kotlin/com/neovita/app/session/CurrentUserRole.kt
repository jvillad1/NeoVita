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

    val isEmployer: Boolean get() = _role.value == "EMPLOYER"

    private var pedido = false

    suspend fun ensureLoaded(userRepo: UserRepository) {
        if (pedido) return
        pedido = true
        _role.value = userRepo.getMe().getOrNull()?.role
    }

    /** Al cerrar sesión: el rol del siguiente usuario no tiene por qué ser el mismo. */
    fun clear() {
        pedido = false
        _role.value = null
    }
}
