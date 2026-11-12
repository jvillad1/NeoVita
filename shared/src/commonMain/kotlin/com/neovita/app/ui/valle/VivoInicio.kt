package com.neovita.app.ui.valle

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neovita.app.screens.dashboard.primerNombre
import com.neovita.shared.domain.model.PillarScores

/** Lo visible del valle entre el saludo y la hoja del puntaje. */
private val EscenaVisible = 250.dp
/** Cuánto se monta la hoja del puntaje sobre el valle. */
private val Solape = 70.dp
/** En la web las tarjetas no se estiran más allá de esto; el valle sí ocupa todo el ancho. */
private val AnchoTarjetas = 640.dp

/**
 * Inicio en la variante Valle 1 · Vivo, la que eligió la familia para probar: el valle del
 * momento del día con el saludo, y una hoja encima con el puntaje, los pilares y el botón grande
 * de la caminata. Al marcarla sube tu cabina amarilla y florecen los guayacanes. Debajo, el Coach
 * y la constancia.
 *
 * Sólo muestra datos reales: sin evaluación no hay puntaje inventado, y no hay pilar "Bienestar"
 * porque ese dato no existe (el Inicio anterior lo dejaba fijo en 84).
 */
@Composable
fun VivoCabecera(
    nombre: String?,
    puntajes: PillarScores?,
    diasSeguidos: Int,
    caminataHecha: Boolean,
    actualizandoCaminata: Boolean,
    florecido: Boolean,
    onCaminata: () -> Unit,
    onCoach: () -> Unit,
    hora: Int = horaLocal(),
) {
    val momento = momentoDelDia(hora)
    val p = paletaValle(momento)

    Column(Modifier.fillMaxWidth().background(p.hoja)) {
        Column(
            Modifier.fillMaxWidth().valleEscena(
                momento,
                florecido = florecido,
                alturaEscena = EscenaVisible + Solape,
                cabinaLlega = caminataHecha,
            )
        ) {
            Saludo(hora, nombre, Modifier.padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 12.dp))
            Spacer(Modifier.height(EscenaVisible + Solape))
        }

        Column(
            Modifier
                .align(Alignment.CenterHorizontally)
                .widthIn(max = AnchoTarjetas)
                .padding(horizontal = 12.dp)
                .montarSobre(Solape),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HojaPuntaje(p, puntajes, caminataHecha, actualizandoCaminata, onCaminata)
            TarjetaCoach(p, onCoach)
            TarjetaConstancia(p, diasSeguidos)
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Sube el elemento [cuanto] y le quita ese alto al layout: queda montado sobre el de arriba. */
private fun Modifier.montarSobre(cuanto: Dp) = layout { medible, restricciones ->
    val pieza = medible.measure(restricciones)
    val y = cuanto.roundToPx()
    layout(pieza.width, (pieza.height - y).coerceAtLeast(0)) { pieza.place(0, -y) }
}

@Composable
private fun Saludo(hora: Int, nombreCompleto: String?, modifier: Modifier = Modifier) {
    val nombre = primerNombre(nombreCompleto)
    Column(modifier.semantics(mergeDescendants = true) { heading() }) {
        if (nombre != null) {
            Text("${saludoDeLaHora(hora)},", style = cuerpo(19.sp, FontWeight.Medium, Color.White))
            Text(nombre, style = titular(54.sp, Color.White, 58.sp))
        } else {
            Text(saludoDeLaHora(hora), style = titular(46.sp, Color.White, 50.sp))
        }
    }
}

@Composable
private fun HojaPuntaje(
    p: VallePaleta,
    puntajes: PillarScores?,
    hecha: Boolean,
    actualizando: Boolean,
    onCaminata: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(34.dp),
        color = p.hoja,
        shadowElevation = 14.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (puntajes != null) {
                val etiqueta = etiquetaPuntaje(puntajes.overall)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.clearAndSetSemantics {
                        contentDescription = "Tu puntaje de longevidad es ${puntajes.overall}. $etiqueta."
                    },
                ) {
                    Text("${puntajes.overall}", style = titular(88.sp, p.tinta, 88.sp))
                    Column {
                        Text(etiqueta, style = titular(31.sp, p.tinta, 34.sp))
                        Text("Tu puntaje de longevidad", style = cuerpo(16.5.sp, FontWeight.Normal, p.tintaSuave))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pilar(p, puntajes.nutrition, "Nutrición", Modifier.weight(1f))
                    Pilar(p, puntajes.exercise, "Actividad", Modifier.weight(1f))
                    Pilar(p, puntajes.sleep, "Sueño", Modifier.weight(1f))
                }
            } else {
                Text(
                    "Tu puntaje aparece cuando completes tu evaluación.",
                    style = cuerpo(18.sp, FontWeight.Medium, p.tinta),
                    modifier = Modifier.padding(4.dp),
                )
            }
            BotonCaminata(p, hecha, actualizando, onCaminata)
        }
    }
}

@Composable
private fun Pilar(p: VallePaleta, valor: Int, nombre: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(p.tarjeta)
            .padding(vertical = 10.dp, horizontal = 4.dp)
            .clearAndSetSemantics { contentDescription = "$nombre: $valor" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("$valor", style = cuerpo(26.sp, FontWeight.ExtraBold, p.tinta))
        Text(nombre, style = cuerpo(16.sp, FontWeight.Medium, p.tintaSuave), textAlign = TextAlign.Center)
    }
}

@Composable
private fun BotonCaminata(p: VallePaleta, hecha: Boolean, actualizando: Boolean, onCaminata: () -> Unit) {
    val fondo by animateColorAsState(if (hecha) VerdeHecho else p.accion, tween(600), label = "fondoCaminata")
    val tinta = if (hecha) Color.White else p.tintaAccion
    val texto = botonCaminata(hecha)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(fondo)
            .toggleable(value = hecha, enabled = !actualizando, role = Role.Checkbox) { onCaminata() }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(tinta.copy(alpha = .2f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = tinta, modifier = Modifier.size(30.dp))
        }
        Column {
            Text(texto.titulo, style = cuerpo(22.sp, FontWeight.ExtraBold, tinta, 26.sp))
            Text(texto.detalle, style = cuerpo(17.sp, FontWeight.Medium, tinta, 22.sp))
        }
    }
}

@Composable
private fun TarjetaCoach(p: VallePaleta, onCoach: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(p.tarjeta).padding(18.dp),
    ) {
        Text("Tu Coach IA", style = cuerpo(16.5.sp, FontWeight.Bold, p.tintaSuave))
        Spacer(Modifier.height(6.dp))
        Text(
            "Cuéntale cómo dormiste y te propone algo sencillo para hoy.",
            style = titular(27.sp, p.tinta, 31.sp),
        )
        Row(
            Modifier
                .heightIn(min = 56.dp)
                .clickable(role = Role.Button, onClick = onCoach)
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Hablar con el Coach", style = cuerpo(17.sp, FontWeight.Bold, p.tinta))
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = p.tinta, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun TarjetaConstancia(p: VallePaleta, dias: Int) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(p.tarjeta).padding(18.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Text("Tu constancia", style = cuerpo(16.5.sp, FontWeight.Bold, p.tintaSuave))
        Spacer(Modifier.height(4.dp))
        Text(textoConstancia(dias), style = titular(36.sp, p.tinta, 40.sp))
        Spacer(Modifier.height(4.dp))
        Text(notaConstancia(dias), style = cuerpo(16.5.sp, FontWeight.Normal, p.tintaSuave, 23.sp))
    }
}

@Composable
private fun titular(tamano: TextUnit, color: Color, interlineado: TextUnit = TextUnit.Unspecified) =
    TextStyle(fontFamily = valleTitulares(), fontSize = tamano, lineHeight = interlineado, color = color)

/** copy de LocalTextStyle para heredar Roboto: en la web no hay letra por defecto. */
@Composable
private fun cuerpo(tamano: TextUnit, peso: FontWeight, color: Color, interlineado: TextUnit = TextUnit.Unspecified) =
    LocalTextStyle.current.copy(fontSize = tamano, fontWeight = peso, color = color, lineHeight = interlineado)
