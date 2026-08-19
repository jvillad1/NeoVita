package com.neovita.app.screens.dashboard

import kotlin.test.Test
import kotlin.test.assertEquals

class GreetingTest {

    @Test fun `greets by first name only`() {
        assertEquals("Hola, Juan", saludoDashboard("Juan Camilo Villada"))
    }

    @Test fun `a single-word name works`() {
        assertEquals("Hola, Ana", saludoDashboard("Ana"))
    }

    @Test fun `no name greets without inventing one`() {
        // Antes había un nombre inventado en el código; un hueco es mejor que una mentira.
        assertEquals("Hola", saludoDashboard(null))
        assertEquals("Hola", saludoDashboard(""))
        assertEquals("Hola", saludoDashboard("   "))
    }

    @Test fun `surrounding whitespace does not leak into the greeting`() {
        assertEquals("Hola, Ana", saludoDashboard("  Ana María  "))
    }
}
