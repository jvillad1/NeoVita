package com.neovita.app.screens.profile

import com.neovita.shared.network.dto.ManualMetricsRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MetricsInputTest {

    private fun build(m: MetricsState) = buildMetricsRequest(m, "2026-09-26")

    @Test
    fun `a Spanish decimal comma is accepted for the weight`() {
        // Con el teclado en español "72,5" es lo que se escribe; rechazarlo era que el peso
        // "no se dejaba ingresar".
        val r = build(MetricsState(weightKg = "72,5")) as MetricsInput.Ok
        assertEquals(72.5, r.request.weightKg)
    }

    @Test
    fun `steps accept a thousands separator`() {
        val r = build(MetricsState(steps = "8.000")) as MetricsInput.Ok
        assertEquals(8000, r.request.steps)
    }

    @Test
    fun `a full form maps every field and carries the date`() {
        val r = build(
            MetricsState(steps = "8000", weightKg = "72.5", bloodPressureSys = "120", bloodPressureDia = "80", glucoseMgdl = "95")
        ) as MetricsInput.Ok
        assertEquals(
            ManualMetricsRequest("2026-09-26", 8000, 72.5, 120, 80, 95),
            r.request
        )
    }

    @Test
    fun `blank fields are omitted so they do not overwrite anything`() {
        val r = build(MetricsState(glucoseMgdl = "95")) as MetricsInput.Ok
        assertEquals(ManualMetricsRequest(date = "2026-09-26", glucoseMgdl = 95), r.request)
    }

    @Test
    fun `an empty form is rejected`() {
        assertTrue(build(MetricsState()) is MetricsInput.Invalid)
        assertTrue(build(MetricsState(steps = "  ", weightKg = " ")) is MetricsInput.Invalid)
    }

    @Test
    fun `blood pressure needs both numbers`() {
        assertTrue(build(MetricsState(bloodPressureSys = "120")) is MetricsInput.Invalid)
        assertTrue(build(MetricsState(bloodPressureDia = "80")) is MetricsInput.Invalid)
    }

    @Test
    fun `text that is not a number says which field to review`() {
        val r = build(MetricsState(weightKg = "setenta")) as MetricsInput.Invalid
        assertTrue("Peso" in r.message, r.message)
    }

    @Test
    fun `a decimal glucose is rejected instead of silently becoming 955`() {
        assertTrue(build(MetricsState(glucoseMgdl = "95.5")) is MetricsInput.Invalid)
    }
}
