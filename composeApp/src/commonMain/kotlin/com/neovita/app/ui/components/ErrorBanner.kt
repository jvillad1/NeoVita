package com.neovita.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.neovita.app.ui.theme.NeoNavy
import com.neovita.app.ui.theme.NeoTeal200

@Composable
fun ErrorBanner(
    message: String,
    onDismiss: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(visible = message.isNotEmpty(), modifier = modifier) {
        Row(
            Modifier.fillMaxWidth().background(NeoNavy).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                message, color = Color.White, modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall
            )
            onRetry?.let {
                TextButton(onClick = it) { Text("Reintentar", color = NeoTeal200) }
            }
        }
    }
}
