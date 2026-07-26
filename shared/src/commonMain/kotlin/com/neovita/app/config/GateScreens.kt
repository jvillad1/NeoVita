package com.neovita.app.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
internal fun UpdateRequiredScreen() {
    GateScaffold(
        title = "Actualiza la app",
        message = "Hay una nueva versión de NeoVita. Actualízala desde la tienda para continuar."
    )
}

@Composable
internal fun MaintenanceScreen(onRetry: () -> Unit) {
    GateScaffold(
        title = "Estamos en mantenimiento",
        message = "Volvemos en unos minutos. Gracias por tu paciencia."
    ) {
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry) { Text("Reintentar") }
    }
}

@Composable
private fun GateScaffold(title: String, message: String, extra: @Composable () -> Unit = {}) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            extra()
        }
    }
}
