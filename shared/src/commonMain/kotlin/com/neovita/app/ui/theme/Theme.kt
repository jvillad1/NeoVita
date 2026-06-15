package com.neovita.app.ui.theme

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

@Composable
fun NeoVitaTheme(content: @Composable () -> Unit) {
    val fontFamily = appFontFamily()
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = NeoTeal700,
            onPrimary = Color.White,
            background = NeoBg,
            surface = NeoSurface,
            secondary = NeoNavy
        ),
        typography = NeoTypography.withFontFamily(fontFamily)
    ) {
        // Also seed LocalTextStyle so ad-hoc Text(...) (e.g. emoji icons that set only
        // fontSize) inherit the emoji-capable family and render correctly on web.
        CompositionLocalProvider(
            LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = fontFamily),
            content = content
        )
    }
}
