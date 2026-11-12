package com.neovita.app.ui.valle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MomentoDelDiaTest {

    @Test fun `each hour of the day maps to the sky you would see in Medellin`() {
        val esperado = mapOf(
            0 to MomentoDelDia.Noche, 4 to MomentoDelDia.Noche,
            5 to MomentoDelDia.Amanecer, 9 to MomentoDelDia.Amanecer,
            10 to MomentoDelDia.Mediodia, 14 to MomentoDelDia.Mediodia,
            15 to MomentoDelDia.Atardecer, 18 to MomentoDelDia.Atardecer,
            19 to MomentoDelDia.Noche, 23 to MomentoDelDia.Noche,
        )
        esperado.forEach { (hora, momento) -> assertEquals(momento, momentoDelDia(hora), "hora $hora") }
    }

    @Test fun `madrugada is night, not dawn`() {
        // El lienzo usaba "hora < 10 → amanecer", así que a las 2 a. m. salía el sol.
        (0..4).forEach { assertEquals(MomentoDelDia.Noche, momentoDelDia(it)) }
    }

    @Test fun `an impossible hour is rejected instead of silently picking a sky`() {
        assertFailsWith<IllegalArgumentException> { momentoDelDia(24) }
        assertFailsWith<IllegalArgumentException> { momentoDelDia(-1) }
    }
}

class SaludoValleTest {

    @Test fun `greeting follows the clock and uses the first name`() {
        assertEquals("Buenos días, Juan", saludoValle(7, "Juan Guillermo Villada"))
        assertEquals("Buenos días, Juan", saludoValle(11, "Juan Guillermo"))
        assertEquals("Buenas tardes, Juan", saludoValle(12, "Juan"))
        assertEquals("Buenas tardes, Juan", saludoValle(18, "Juan"))
        assertEquals("Buenas noches, Juan", saludoValle(19, "Juan"))
        assertEquals("Buenas noches, Juan", saludoValle(2, "Juan"))
    }

    @Test fun `without a name it greets without inventing one`() {
        assertEquals("Buenas tardes", saludoValle(15, null))
        assertEquals("Buenas tardes", saludoValle(15, "   "))
    }
}

class VallePaletaContrasteTest {

    private fun assertContraste(minimo: Double, a: androidx.compose.ui.graphics.Color, b: androidx.compose.ui.graphics.Color, que: String) {
        val r = contraste(a, b)
        assertTrue(r >= minimo, "$que: contraste $r < $minimo")
    }

    @Test fun `contrast helper matches WCAG reference values`() {
        val blanco = androidx.compose.ui.graphics.Color.White
        val negro = androidx.compose.ui.graphics.Color.Black
        assertEquals(21.0, contraste(blanco, negro), 0.01)
        assertEquals(1.0, contraste(blanco, blanco), 0.01)
    }

    @Test fun `every moment keeps text readable for people over 55`() {
        MomentoDelDia.entries.forEach { m ->
            val p = paletaValle(m)
            assertContraste(4.5, p.tinta, p.hoja, "$m tinta/hoja")
            assertContraste(4.5, p.tintaSuave, p.hoja, "$m tintaSuave/hoja")
            assertContraste(4.5, p.tinta, p.tarjeta, "$m tinta/tarjeta")
            assertContraste(4.5, p.tintaSuave, p.tarjeta, "$m tintaSuave/tarjeta")
            assertContraste(4.5, p.tintaAccion, p.accion, "$m botón")
            assertContraste(4.5, androidx.compose.ui.graphics.Color.White, VerdeHecho, "$m botón hecho")
            // El saludo va en blanco sobre la parte alta del cielo.
            assertContraste(4.5, androidx.compose.ui.graphics.Color.White, p.cielo.first(), "$m saludo/cielo")
        }
    }
}
