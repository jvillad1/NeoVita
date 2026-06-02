package com.neovita.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.neovita.app.ui.theme.*

@Composable
fun ScoreRing(
    score: Int,
    size: Dp = 96.dp,
    label: String = "",
    textColor: Color = Color.Unspecified,
    modifier: Modifier = Modifier
) {
    val ringColor = when {
        score >= 80 -> NeoCrimson
        score >= 60 -> NeoAmber
        else        -> NeoRed
    }
    val resolvedTextColor = if (textColor == Color.Unspecified) ringColor else textColor

    // Animate sweep from 0 to target
    var triggered by remember { mutableStateOf(false) }
    LaunchedEffect(score) { triggered = true }
    val sweep by animateFloatAsState(
        targetValue = if (triggered) 360f * score / 100f else 0f,
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "scoreRingSweep"
    )

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = this.size.width * 0.1f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(stroke / 2, stroke / 2)
            drawArc(Color.Gray.copy(alpha = 0.25f), -90f, 360f, false,
                style = Stroke(stroke, cap = StrokeCap.Round), topLeft = topLeft, size = arcSize)
            drawArc(ringColor, -90f, sweep, false,
                style = Stroke(stroke, cap = StrokeCap.Round), topLeft = topLeft, size = arcSize)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$score", style = MaterialTheme.typography.titleLarge,
                color = resolvedTextColor, fontWeight = FontWeight.Bold)
            if (label.isNotEmpty())
                Text(label, style = MaterialTheme.typography.labelSmall, color = resolvedTextColor.copy(alpha = 0.7f))
        }
    }
}
