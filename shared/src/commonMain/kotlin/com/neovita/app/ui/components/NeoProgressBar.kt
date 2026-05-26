package com.neovita.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.neovita.app.ui.theme.NeoAmber
import com.neovita.app.ui.theme.NeoRed
import com.neovita.app.ui.theme.NeoTeal700

@Composable
fun NeoProgressBar(
    label: String,
    score: Int,
    modifier: Modifier = Modifier
) {
    val color = when {
        score >= 80 -> NeoTeal700
        score >= 60 -> NeoAmber
        else -> NeoRed
    }
    Column(modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                "$score", style = MaterialTheme.typography.bodyMedium,
                color = color
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { score / 100f },
            modifier = Modifier.fillMaxWidth(),
            color = color,
            trackColor = Color.LightGray.copy(alpha = 0.3f)
        )
    }
}
