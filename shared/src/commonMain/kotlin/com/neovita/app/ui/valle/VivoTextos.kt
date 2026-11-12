package com.neovita.app.ui.valle

/** Hábito del botón grande de Inicio (Valle Vivo). El servidor lo valida en HabitRoutes. */
const val HABITO_CAMINATA = "walk_20min"

/** Etiqueta junto al puntaje. Ánimo en todos los niveles: nunca un veredicto. */
fun etiquetaPuntaje(puntaje: Int): String = when {
    puntaje >= 80 -> "Vas firme"
    puntaje >= 60 -> "Vas bien"
    puntaje >= 40 -> "Vamos subiendo"
    else -> "Empecemos juntos"
}

fun textoConstancia(dias: Int): String = when {
    dias <= 0 -> "Hoy puede ser el primero"
    dias == 1 -> "1 día seguido"
    else -> "$dias días seguidos"
}

/** Debajo de la constancia. Sin amenazas de perder la racha (ver VivoTextosTest). */
fun notaConstancia(dias: Int): String = when {
    dias <= 0 -> "Basta con un hábito para empezar a contar."
    dias < 7 -> "Cada día que cumples suma. Ve a tu ritmo."
    else -> "Tu valle lo nota: los guayacanes florecen contigo."
}

data class TextoBoton(val titulo: String, val detalle: String)

fun botonCaminata(hecha: Boolean): TextoBoton =
    if (hecha) TextoBoton("Caminata de hoy: hecha", "Tu cabina va llegando y florecieron los guayacanes")
    else TextoBoton("Hoy: camina 20 minutos", "Toca aquí cuando la hagas")
