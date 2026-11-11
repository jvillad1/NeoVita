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
import com.neovita.app.resources.DMSerifDisplay_Regular
import com.neovita.app.resources.Res
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.Font

/** Letra de los titulares de Valle. El cuerpo sigue en Roboto, que trae los emoji fusionados. */
@Composable
fun valleTitulares(): FontFamily = FontFamily(Font(Res.font.DMSerifDisplay_Regular, FontWeight.Normal))

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

/**
 * Encabezado de Inicio: el saludo sobre una franja de cielo liso y, debajo, el valle del momento
 * del día. El texto va en blanco sobre el color más alto del cielo, que la paleta garantiza a
 * 4.5:1 (VallePaletaContrasteTest); la escena empieza donde termina el texto.
 */
@Composable
fun ValleEncabezado(
    nombre: String?,
    modifier: Modifier = Modifier,
    florecido: Boolean = false,
    alturaEscena: Dp = 230.dp,
    subtitulo: String = "Tu viaje hacia la longevidad comienza hoy.",
    /** Hora 0..23 que decide cielo y saludo; por defecto la del reloj. Fijarla sirve para pruebas. */
    hora: Int = horaLocal(),
) {
    Column(
        modifier.fillMaxWidth().valleEscena(momentoDelDia(hora), florecido, alturaEscena = alturaEscena)
    ) {
        Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 16.dp)) {
            Text(
                saludoValle(hora, nombre),
                modifier = Modifier.semantics { heading() },
                style = TextStyle(fontFamily = valleTitulares(), fontSize = 40.sp, lineHeight = 44.sp, color = Color.White),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                subtitulo,
                // copy de LocalTextStyle para heredar Roboto (en web no hay letra por defecto).
                style = LocalTextStyle.current.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium, color = Color.White),
            )
        }
        Spacer(Modifier.height(alturaEscena))
    }
}
