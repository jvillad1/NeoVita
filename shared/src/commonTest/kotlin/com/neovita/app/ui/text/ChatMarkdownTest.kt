package com.neovita.app.ui.text

import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Las respuestas del coach llegan en Markdown y la burbuja las pintaba literales: se leía
 * "**Rutinas nocturnas:**" con los asteriscos, y los títulos con su almohadilla delante.
 *
 * No se usa una librería: el modelo emite un subconjunto muy pequeño (negrita, títulos,
 * viñetas, separadores) y meter una dependencia nueva para eso no se paga.
 */
class ChatMarkdownTest {

    private fun render(raw: String) = chatMarkdown(raw)

    @Test
    fun `bold markers disappear from the visible text`() {
        val a = render("**Rutinas nocturnas:** dormí bien")
        assertEquals("Rutinas nocturnas: dormí bien", a.text)
    }

    @Test
    fun `bold text is actually bold, not just stripped`() {
        val a = render("hola **mundo** chau")
        val negrita = a.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }
        assertEquals(1, negrita.size, "no se aplicó negrita: ${a.spanStyles}")
        assertEquals("mundo", a.text.substring(negrita[0].start, negrita[0].end))
    }

    @Test
    fun `heading markers are removed and the title is emphasised`() {
        val a = render("# Consejos para dormir")
        assertEquals("Consejos para dormir", a.text)
        assertTrue(a.spanStyles.any { it.item.fontWeight == FontWeight.Bold }, "el título no destaca")
    }

    @Test
    fun `list items become real bullets`() {
        val a = render("- uno\n- dos")
        assertEquals("•  uno\n•  dos", a.text)
    }

    @Test
    fun `a horizontal rule does not leak three dashes into the text`() {
        val a = render("arriba\n---\nabajo")
        assertTrue("---" !in a.text, "el separador se coló como texto: ${a.text}")
    }

    @Test
    fun `plain text passes through untouched`() {
        val crudo = "Sin nada especial, sólo texto. ¿Todo bien?"
        assertEquals(crudo, render(crudo).text)
    }

    @Test
    fun `a lone asterisk is not treated as formatting`() {
        // Un asterisco suelto (multiplicación, una nota al pie) no debe comerse el resto.
        val a = render("2 * 3 = 6")
        assertEquals("2 * 3 = 6", a.text)
    }

    @Test
    fun `an unclosed bold marker leaves the text readable`() {
        // Mientras el stream llega, la burbuja puede tener "**Rutinas" a medio escribir.
        val a = render("**Rutinas noct")
        assertTrue("Rutinas noct" in a.text, "se perdió el texto a medio llegar: ${a.text}")
    }
}
