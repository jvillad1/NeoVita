package com.neovita.app.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neovita.app.ui.theme.NeoBg

/**
 * Ancho máximo del contenido. El diseño es de móvil: estirado a 2000 px en un monitor
 * las tarjetas quedan larguísimas y el texto ilegible por longitud de línea.
 *
 * 1100 dp es holgado para el dashboard de dos columnas y sigue por debajo de cualquier
 * portátil, así que en pantallas normales no se nota el recorte.
 */
private val MAX_CONTENT_WIDTH = 1100.dp

/**
 * Centra el contenido y le pone un techo de ancho. En móvil no cambia nada: la pantalla
 * es más estrecha que el máximo, así que `widthIn` no recorta.
 */
@Composable
fun MaxWidthContent(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(NeoBg),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(Modifier.widthIn(max = MAX_CONTENT_WIDTH).fillMaxSize()) {
            content()
        }
    }
}
