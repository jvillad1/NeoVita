package com.neovita.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun NeoVitaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = NeoTeal700,
            onPrimary = Color.White,
            background = NeoBg,
            surface = NeoSurface,
            secondary = NeoNavy
        ),
        typography = NeoTypography,
        content = content
    )
}
