package com.neovita.app.ui.text

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Convierte el Markdown que emite el coach en texto con formato.
 *
 * Deliberadamente parcial: el modelo usa negritas, títulos, viñetas y algún separador, y
 * nada más. Una librería de Markdown completa sería una dependencia nueva en los cuatro
 * targets para cubrir casos que no ocurren.
 *
 * Regla de oro: **nunca perder texto**. La respuesta llega en streaming, así que a mitad de
 * camino hay marcadores sin cerrar ("**Rutinas noct"); ante la duda se muestra tal cual en
 * vez de tragarse lo que aún no terminó de llegar.
 */
fun chatMarkdown(raw: String): AnnotatedString = buildAnnotatedString {
    val lineas = raw.split("\n")
    lineas.forEachIndexed { i, linea ->
        val limpia = linea.trimEnd()
        when {
            // Separador: no aporta nada en una burbuja de chat y "---" como texto es ruido.
            limpia.trim().matches(Regex("^-{3,}$|^\\*{3,}$")) -> return@forEachIndexed

            limpia.trimStart().startsWith("#") -> {
                val texto = limpia.trimStart().trimStart('#').trim()
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp)) {
                    appendInline(texto)
                }
            }

            limpia.trimStart().startsWith("- ") || limpia.trimStart().startsWith("* ") -> {
                append("•  ")
                appendInline(limpia.trimStart().drop(2))
            }

            else -> appendInline(limpia)
        }
        if (i != lineas.lastIndex) append("\n")
    }
}

/**
 * Marcadores dentro de una línea. Se resuelve **negrita** antes que *cursiva* porque `**`
 * empieza por `*` y el orden inverso partiría las negritas por la mitad.
 */
private fun AnnotatedString.Builder.appendInline(texto: String) {
    var resto = texto
    while (resto.isNotEmpty()) {
        val negrita = encontrarPar(resto, "**")
        val cursiva = encontrarPar(resto, "*").takeIf { it != null && negrita == null }
        val marca = negrita ?: cursiva
        if (marca == null) {
            append(resto)
            return
        }
        append(resto.substring(0, marca.inicio))
        val estilo = if (marca.delimitador == "**") SpanStyle(fontWeight = FontWeight.Bold)
                     else SpanStyle(fontStyle = FontStyle.Italic)
        withStyle(estilo) { append(marca.contenido) }
        resto = resto.substring(marca.fin)
    }
}

private class Marca(val inicio: Int, val fin: Int, val contenido: String, val delimitador: String)

/** Busca un par abierto+cerrado. Sin cierre devuelve null: el texto se deja intacto. */
private fun encontrarPar(texto: String, delim: String): Marca? {
    val abre = texto.indexOf(delim)
    if (abre < 0) return null
    val cierra = texto.indexOf(delim, abre + delim.length)
    if (cierra < 0) return null
    val contenido = texto.substring(abre + delim.length, cierra)
    // "2 * 3 = 6": entre asteriscos sólo hay espacios, no es formato.
    if (contenido.isBlank()) return null
    return Marca(abre, cierra + delim.length, contenido, delim)
}
