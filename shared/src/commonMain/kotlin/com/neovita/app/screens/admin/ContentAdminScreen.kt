package com.neovita.app.screens.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.neovita.app.ui.theme.NeoCrimson
import com.neovita.shared.network.dto.ContentItemDto
import com.neovita.shared.network.dto.ContentRequest
import org.koin.compose.koinInject

private val CATEGORIES = listOf("NUTRITION", "EXERCISE", "SLEEP", "MENTAL_HEALTH", "GENERAL")
private val TYPES = listOf("ARTICLE", "TIP", "VIDEO")

class ContentAdminScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val vm: ContentAdminViewModel = koinInject()
        val state by vm.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow

        var editing by remember { mutableStateOf<ContentItemDto?>(null) }
        var creating by remember { mutableStateOf(false) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Administrar contenido") },
                    navigationIcon = {
                        TextButton(onClick = { navigator.pop() }) { Text("‹ Atrás") }
                    }
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { creating = true },
                    containerColor = NeoCrimson,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) { Text("+ Nuevo") }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when {
                    state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    state.error != null -> Text(
                        state.error!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp)
                    )
                    else -> LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(state.items, key = { it.id }) { item ->
                            ContentRow(
                                item = item,
                                onEdit = { editing = item },
                                onDelete = { vm.delete(item.id) }
                            )
                        }
                    }
                }
            }
        }

        if (creating) {
            ContentFormDialog(
                initial = null,
                saving = state.saving,
                onDismiss = { creating = false },
                onSave = { req -> vm.save(null, req) { creating = false } }
            )
        }
        editing?.let { item ->
            ContentFormDialog(
                initial = item,
                saving = state.saving,
                onDismiss = { editing = null },
                onSave = { req -> vm.save(item.id, req) { editing = null } }
            )
        }
    }
}

@Composable
private fun ContentRow(item: ContentItemDto, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(shape = RoundedCornerShape(12.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 2)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${item.category} · ${item.type} · ${item.readMinutes} min" +
                        if (!item.active) " · (inactivo)" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onEdit) { Text("Editar") }
            TextButton(onClick = onDelete) { Text("Borrar", color = MaterialTheme.colorScheme.error) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContentFormDialog(
    initial: ContentItemDto?,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (ContentRequest) -> Unit,
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var teaser by remember { mutableStateOf(initial?.teaser ?: "") }
    var category by remember { mutableStateOf(initial?.category ?: CATEGORIES.first()) }
    var type by remember { mutableStateOf(initial?.type ?: TYPES.first()) }
    var readMinutes by remember { mutableStateOf((initial?.readMinutes ?: 3).toString()) }
    var sortOrder by remember { mutableStateOf((initial?.sortOrder ?: 0).toString()) }
    var active by remember { mutableStateOf(initial?.active ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nuevo contenido" else "Editar contenido") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(title, { title = it }, label = { Text("Título") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(teaser, { teaser = it }, label = { Text("Resumen") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                Dropdown("Categoría", CATEGORIES, category) { category = it }
                Dropdown("Tipo", TYPES, type) { type = it }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        readMinutes, { readMinutes = it.filter(Char::isDigit) },
                        label = { Text("Minutos") }, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        sortOrder, { sortOrder = it.filter(Char::isDigit) },
                        label = { Text("Orden") }, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(active, { active = it })
                    Spacer(Modifier.width(8.dp))
                    Text("Activo (visible en el dashboard)")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        ContentRequest(
                            title = title.trim(),
                            category = category,
                            type = type,
                            teaser = teaser.trim(),
                            readMinutes = readMinutes.toIntOrNull() ?: 0,
                            sortOrder = sortOrder.toIntOrNull() ?: 0,
                            active = active,
                        )
                    )
                },
                enabled = !saving && title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = NeoCrimson)
            ) { Text(if (saving) "Guardando…" else "Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Dropdown(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(text = { Text(opt) }, onClick = { onSelect(opt); expanded = false })
            }
        }
    }
}
