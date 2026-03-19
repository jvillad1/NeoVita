package com.neovita.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.neovita.app.ui.theme.NeoAmber
import com.neovita.app.ui.theme.NeoRed
import com.neovita.app.ui.theme.NeoTeal700

@Composable
fun ScoreRing(
    score: Int,
    size: Dp = 96.dp,
    label: String = "",
    modifier: Modifier = Modifier
) {
    val color = when {
        score >= 80 -> NeoTeal700
        score >= 60 -> NeoAmber
        else -> NeoRed
    }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = this.size.width * 0.1f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(stroke / 2, stroke / 2)
            drawArc(
                Color.LightGray.copy(alpha = 0.3f), -90f, 360f, false,
                style = Stroke(stroke, cap = StrokeCap.Round),
                topLeft = topLeft, size = arcSize
            )
            drawArc(
                color, -90f, 360f * score / 100f, false,
                style = Stroke(stroke, cap = StrokeCap.Round),
                topLeft = topLeft, size = arcSize
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$score",
                style = MaterialTheme.typography.titleLarge,
                color = color, fontWeight = FontWeight.Bold
            )
            if (label.isNotEmpty())
                Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}
