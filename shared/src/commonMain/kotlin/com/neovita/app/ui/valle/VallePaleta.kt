package com.neovita.app.ui.valle

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

/**
 * Colores de un momento del día. Salen del lienzo de diseño (scratchpad valle2/kit.py) para que
 * la app y los videos que vio la familia sean la misma cosa.
 *
 * Escena: [cielo] (de arriba hacia abajo, con sus [paradasCielo]), montañas [lejana]/[media]/
 * [cercana], [sol] (luna de noche), [nube], ladrillo de los barrios [ladrillo] y sus [techo],
 * árboles [hoja] → guayacán florecido [flor], [tronco]. [estrellas] y [luces] son opacidades.
 *
 * Interfaz: [hoja] es el fondo de las pantallas, [tarjeta] el de las tarjetas, [tinta] y
 * [tintaSuave] el texto, [accion]/[tintaAccion] el botón principal. Todos los pares de texto
 * pasan 4.5:1 (ver VallePaletaContrasteTest).
 */
data class VallePaleta(
    val cielo: List<Color>,
    val paradasCielo: List<Float>,
    val lejana: Color,
    val media: Color,
    val cercana: Color,
    val sol: Color,
    val nube: Color,
    val estrellas: Float,
    val luces: Float,
    val pajaros: Float,
    val ladrillo: List<Color>,
    val techo: Color,
    val hojaArbol: Color,
    val flor: Color,
    val tronco: Color,
    /** Posición del sol como fracción del ancho/alto de la escena. */
    val solX: Float,
    val solY: Float,
    val hoja: Color,
    val tarjeta: Color,
    val tinta: Color,
    val tintaSuave: Color,
    val accion: Color,
    val tintaAccion: Color,
)

private val Atardecer = VallePaleta(
    cielo = listOf(Color(0xFF2A1458), Color(0xFF9C2F7C), Color(0xFFF07A52), Color(0xFFFFB05A)),
    paradasCielo = listOf(0f, .48f, .78f, 1f),
    lejana = Color(0xFF8A4A92), media = Color(0xFF5F2C74), cercana = Color(0xFF2E1547),
    sol = Color(0xFFFFC56B), nube = Color(0xFFFFB7A1),
    estrellas = 0f, luces = .85f, pajaros = 1f,
    ladrillo = listOf(Color(0xFFB8574F), Color(0xFF9A4A5C), Color(0xFF804066)), techo = Color(0xFF4E2150),
    hojaArbol = Color(0xFF4C7A52), flor = Color(0xFFFFC928), tronco = Color(0xFF3A2030),
    solX = .72f, solY = .26f,
    hoja = Color(0xFFFFF8F0), tarjeta = Color(0xFFF4E7E2),
    tinta = Color(0xFF26182F), tintaSuave = Color(0xFF5C4E66),
    accion = Color(0xFFA9265C), tintaAccion = Color.White,
)

private val Amanecer = Atardecer.copy(
    cielo = listOf(Color(0xFF34297A), Color(0xFFB85A86), Color(0xFFFFAE86), Color(0xFFFFD99C)),
    paradasCielo = listOf(0f, .52f, .84f, 1f),
    lejana = Color(0xFFBE7399), media = Color(0xFF85508B), cercana = Color(0xFF3F2A63),
    sol = Color(0xFFFFE08A), nube = Color(0xFFFFD2C0), luces = .45f,
    ladrillo = listOf(Color(0xFFD47C68), Color(0xFFB96476), Color(0xFF94547F)), techo = Color(0xFF6A3B6B),
    solX = .2f, solY = .36f,
    accion = Color(0xFF7D2C72),
)

private val Mediodia = Atardecer.copy(
    cielo = listOf(Color(0xFF0F5FAE), Color(0xFF3E9FE6), Color(0xFFBFE7FB)),
    paradasCielo = listOf(0f, .55f, 1f),
    lejana = Color(0xFF6DB39A), media = Color(0xFF3A9170), cercana = Color(0xFF14583F),
    sol = Color(0xFFFFF1A8), nube = Color.White, luces = 0f,
    ladrillo = listOf(Color(0xFFDA7A4C), Color(0xFFC4603C), Color(0xFFE8A36B)), techo = Color(0xFF8E3F2B),
    hojaArbol = Color(0xFF2F8A55), tronco = Color(0xFF3C2A20),
    // Más bajo que en el lienzo (.18): el halo de un sol alto aclaraba el cielo bajo el saludo.
    solX = .75f, solY = .3f,
    hoja = Color(0xFFFBFDF8), tarjeta = Color(0xFFE6EFEA),
    tinta = Color(0xFF10262B), tintaSuave = Color(0xFF3F5660),
    accion = Color(0xFF0E5CA8),
)

private val Noche = Atardecer.copy(
    cielo = listOf(Color(0xFF050A26), Color(0xFF101A52), Color(0xFF27357A)),
    paradasCielo = listOf(0f, .6f, 1f),
    lejana = Color(0xFF2B3A7A), media = Color(0xFF1D285F), cercana = Color(0xFF0B1236),
    sol = Color(0xFFEEF0FF), nube = Color(0xFF2F3C7E),
    estrellas = 1f, luces = 1f, pajaros = 0f,
    ladrillo = listOf(Color(0xFF2E3570), Color(0xFF262D64), Color(0xFF1F2658)), techo = Color(0xFF151B45),
    hojaArbol = Color(0xFF1F3F5F), flor = Color(0xFFD9B64E), tronco = Color(0xFF0F1533),
    solX = .65f, solY = .31f,
    hoja = Color(0xFF0F1538), tarjeta = Color(0xFF1E2755),
    tinta = Color(0xFFF2F3FF), tintaSuave = Color(0xFFC2C7E4),
    accion = Color(0xFFFFD27A), tintaAccion = Color(0xFF1A1433),
)

fun paletaValle(momento: MomentoDelDia): VallePaleta = when (momento) {
    MomentoDelDia.Amanecer -> Amanecer
    MomentoDelDia.Mediodia -> Mediodia
    MomentoDelDia.Atardecer -> Atardecer
    MomentoDelDia.Noche -> Noche
}

/** Razón de contraste WCAG 2.x entre dos colores opacos (1..21). */
fun contraste(a: Color, b: Color): Double {
    val la = a.luminance().toDouble()
    val lb = b.luminance().toDouble()
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
}
