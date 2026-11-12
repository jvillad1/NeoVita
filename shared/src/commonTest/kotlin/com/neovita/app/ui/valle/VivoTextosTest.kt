package com.neovita.app.ui.valle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VivoTextosTest {

    @Test fun `score label is encouraging at every level, never a verdict`() {
        assertEquals("Vas firme", etiquetaPuntaje(86))
        assertEquals("Vas firme", etiquetaPuntaje(80))
        assertEquals("Vas bien", etiquetaPuntaje(79))
        assertEquals("Vas bien", etiquetaPuntaje(60))
        assertEquals("Vamos subiendo", etiquetaPuntaje(59))
        assertEquals("Vamos subiendo", etiquetaPuntaje(40))
        assertEquals("Empecemos juntos", etiquetaPuntaje(39))
        assertEquals("Empecemos juntos", etiquetaPuntaje(0))
    }

    @Test fun `streak reads naturally in Spanish`() {
        assertEquals("Hoy puede ser el primero", textoConstancia(0))
        assertEquals("1 día seguido", textoConstancia(1))
        assertEquals("15 días seguidos", textoConstancia(15))
    }

    @Test fun `streak note never threatens to take anything away`() {
        // Sin culpa ni ansiedad: el regreso tiene que venir del valle, no del miedo a perder.
        listOf(0, 1, 7, 30).forEach { dias ->
            val nota = notaConstancia(dias).lowercase()
            listOf("pierdes", "perder", "romper", "rompes", "no falles").forEach { amenaza ->
                assertFalse(amenaza in nota, "\"$nota\" contiene \"$amenaza\"")
            }
        }
    }

    @Test fun `walk button tells what to do, then confirms it in plain words`() {
        val pendiente = botonCaminata(hecha = false)
        assertEquals("Hoy: camina 20 minutos", pendiente.titulo)
        assertEquals("Toca aquí cuando la hagas", pendiente.detalle)

        val hecha = botonCaminata(hecha = true)
        assertEquals("Caminata de hoy: hecha", hecha.titulo)
        assertTrue(hecha.detalle.isNotBlank())
    }
}
