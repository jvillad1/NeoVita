package com.neovita.server.routes

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Un delta de Anthropic puede traer saltos de línea (listas, párrafos, títulos markdown).
 * Escribirlo como `data: $chunk` deja las líneas siguientes SIN prefijo, y en SSE una línea
 * que no empieza por `data:` no forma parte del evento: el cliente la descarta en silencio.
 *
 * Efecto observado en producción: el texto perdía trozos justo en los saltos de línea y los
 * fragmentos supervivientes se pegaban entre sí ("...incluso los domingosnjil antes de
 * dormir funciona de maravillaular 1 hora..."). Parecía que el modelo escribía mal.
 */
class SseFramingTest {

    @Test
    fun `a single-line chunk is one data line`() {
        assertEquals("data: hola\n\n", sseFrame("hola"))
    }

    @Test
    fun `every line of a multi-line chunk carries its own data prefix`() {
        assertEquals(
            "data: Duerme mejor\ndata: - acuéstate a la misma hora\n\n",
            sseFrame("Duerme mejor\n- acuéstate a la misma hora")
        )
    }

    @Test
    fun `a blank line inside the chunk survives as an empty data line`() {
        // Sin prefijo, esa línea en blanco terminaría el evento y partiría el mensaje en dos.
        assertEquals("data: uno\ndata: \ndata: dos\n\n", sseFrame("uno\n\ndos"))
    }

    @Test
    fun `carriage returns do not leak into the payload`() {
        // \r\n es un separador de línea válido en SSE; dejar el \r pegado al texto mete
        // basura invisible en la burbuja del chat.
        assertEquals("data: uno\ndata: dos\n\n", sseFrame("uno\r\ndos"))
    }
}
