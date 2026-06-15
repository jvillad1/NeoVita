package com.neovita.app.screens.admin

import com.neovita.shared.domain.repository.ContentRepository
import com.neovita.shared.network.dto.ContentItemDto
import com.neovita.shared.network.dto.ContentRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ContentAdminState(
    val items: List<ContentItemDto> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val saving: Boolean = false,
)

class ContentAdminViewModel(private val repo: ContentRepository) {
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val _state = MutableStateFlow(ContentAdminState())
    val state = _state.asStateFlow()

    init { load() }

    fun load() {
        scope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repo.getAllContent()
                .onSuccess { items -> _state.update { it.copy(items = items, isLoading = false) } }
                .onFailure { _state.update { it.copy(isLoading = false, error = "No se pudo cargar el contenido (¿tu usuario tiene rol EMPLOYER?)") } }
        }
    }

    /** Create when [id] is null, otherwise update. Reloads the list on success. */
    fun save(id: String?, req: ContentRequest, onDone: () -> Unit) {
        scope.launch {
            _state.update { it.copy(saving = true, error = null) }
            val result = if (id == null) repo.create(req) else repo.update(id, req)
            result
                .onSuccess { _state.update { it.copy(saving = false) }; load(); onDone() }
                .onFailure { _state.update { it.copy(saving = false, error = "No se pudo guardar") } }
        }
    }

    fun delete(id: String) {
        scope.launch {
            repo.delete(id)
                .onSuccess { load() }
                .onFailure { _state.update { it.copy(error = "No se pudo eliminar") } }
        }
    }
}
