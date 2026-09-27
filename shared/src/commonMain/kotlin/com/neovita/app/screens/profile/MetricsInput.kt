package com.neovita.app.screens.profile

import com.neovita.shared.network.dto.ManualMetricsRequest

sealed interface MetricsInput {
    data class Ok(val request: ManualMetricsRequest) : MetricsInput
    data class Invalid(val message: String) : MetricsInput
}

/**
 * Convierte lo que se escribió en el formulario en lo que se envía al servidor.
 *
 * El teclado en español escribe la coma decimal ("72,5"); `toDoubleOrNull()` la rechaza, y con
 * eso el peso simplemente "no se dejaba ingresar". Los pasos aceptan separador de miles
 * ("8.000"), los demás enteros no: "95.5" de glucosa no puede volverse 955.
 */
internal fun buildMetricsRequest(m: MetricsState, date: String): MetricsInput {
    fun clean(s: String) = s.trim()
    val steps = clean(m.steps).replace(".", "").replace(",", "").replace(" ", "")
    val weight = clean(m.weightKg).replace(',', '.')
    val sys = clean(m.bloodPressureSys)
    val dia = clean(m.bloodPressureDia)
    val glucose = clean(m.glucoseMgdl)

    if (listOf(steps, weight, sys, dia, glucose).all { it.isEmpty() }) {
        return MetricsInput.Invalid("Ingresa al menos un valor.")
    }
    if ((sys.isEmpty()) != (dia.isEmpty())) {
        return MetricsInput.Invalid("Para la presión ingresa la sistólica y la diastólica.")
    }

    fun bad(label: String, example: String) =
        MetricsInput.Invalid("Revisa «$label»: usa solo números (ej. $example).")

    val stepsV = if (steps.isEmpty()) null else steps.toIntOrNull() ?: return bad("Pasos diarios", "8000")
    val weightV = if (weight.isEmpty()) null else weight.toDoubleOrNull() ?: return bad("Peso", "72,5")
    val sysV = if (sys.isEmpty()) null else sys.toIntOrNull() ?: return bad("Presión sistólica", "120")
    val diaV = if (dia.isEmpty()) null else dia.toIntOrNull() ?: return bad("Presión diastólica", "80")
    val glucoseV = if (glucose.isEmpty()) null else glucose.toIntOrNull() ?: return bad("Glucosa", "95")

    return MetricsInput.Ok(
        ManualMetricsRequest(
            date = date, steps = stepsV, weightKg = weightV,
            bloodPressureSys = sysV, bloodPressureDia = diaV, glucoseMgdl = glucoseV
        )
    )
}
