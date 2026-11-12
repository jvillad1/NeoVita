package com.neovita.app.ui.valle

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Letra de los titulares de Valle (DM Serif Display), o null mientras no esté lista. El cuerpo
 * sigue en Roboto, que trae los emoji fusionados.
 *
 * En la web no sirve `Font(Res.font.…)`: arma el texto con un marcador vacío mientras descarga
 * y no lo reemplaza al terminar, así que los titulares quedaban en Roboto y a veces sin alguna
 * letra ("dmin" por "Admin"). Allí se leen los bytes y la familia se entrega sólo completa.
 */
@Composable
expect fun cargarTitulares(): FontFamily?

/**
 * La familia de titulares, cargada una sola vez en NeoVitaTheme. Dinámico a propósito: con
 * staticCompositionLocalOf el cambio de null a la fuente no llegaba al encabezado de Inicio, que
 * vive dentro de un LazyColumn (otra subcomposición), y se quedaba en Roboto para siempre.
 */
val LocalTitulares = compositionLocalOf<FontFamily?> { null }

/** Titulares si ya cargaron; si no, la letra del cuerpo (Roboto) para no dejar huecos. */
@Composable
fun valleTitulares(): FontFamily? = LocalTitulares.current ?: LocalTextStyle.current.fontFamily

/** Hora local (0..23), refrescada cada minuto para que el cielo cambie solo. */
@Composable
fun horaLocal(): Int {
    fun ahora() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour
    var hora by remember { mutableIntStateOf(ahora()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            hora = ahora()
        }
    }
    return hora
}
