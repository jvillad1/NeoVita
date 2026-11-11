package com.neovita.app.ui.valle

import com.neovita.app.screens.dashboard.primerNombre

/**
 * Los cuatro cielos de la dirección visual "Valle" (votada por la familia el 2026-11-09).
 * La escena y la paleta de la app siguen la hora real: nadie elige un tema.
 */
enum class MomentoDelDia(val nombre: String) {
    Amanecer("Amanecer"),
    Mediodia("Mediodía"),
    Atardecer("Atardecer"),
    Noche("Noche"),
}

/**
 * Cielo que corresponde a [hora] (0..23). La madrugada es noche: el lienzo de diseño usaba
 * "hora < 10 → amanecer" y a las 2 a. m. salía el sol.
 */
fun momentoDelDia(hora: Int): MomentoDelDia {
    require(hora in 0..23) { "hora fuera de rango: $hora" }
    return when (hora) {
        in 5..9 -> MomentoDelDia.Amanecer
        in 10..14 -> MomentoDelDia.Mediodia
        in 15..18 -> MomentoDelDia.Atardecer
        else -> MomentoDelDia.Noche
    }
}

/**
 * Saludo según el reloj (no según el cielo: a las 10 a. m. ya es "mediodía" en la escena pero
 * todavía son "buenos días"). Sin nombre se saluda a secas.
 */
fun saludoValle(hora: Int, nombreCompleto: String?): String {
    require(hora in 0..23) { "hora fuera de rango: $hora" }
    val saludo = when (hora) {
        in 5..11 -> "Buenos días"
        in 12..18 -> "Buenas tardes"
        else -> "Buenas noches"
    }
    val nombre = primerNombre(nombreCompleto)
    return if (nombre == null) saludo else "$saludo, $nombre"
}
