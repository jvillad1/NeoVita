package com.neovita.app.screens.dashboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GreetingTest {

    @Test fun `uses the first name only`() {
        assertEquals("Juan", primerNombre("Juan Camilo Villada"))
    }

    @Test fun `a single-word name works`() {
        assertEquals("Ana", primerNombre("Ana"))
    }

    @Test fun `no usable name gives null instead of inventing one`() {
        // Antes había un nombre inventado en el código; un hueco es mejor que una mentira.
        assertNull(primerNombre(null))
        assertNull(primerNombre(""))
        assertNull(primerNombre("   "))
    }

    @Test fun `surrounding whitespace does not leak into the name`() {
        assertEquals("Ana", primerNombre("  Ana María  "))
    }
}
