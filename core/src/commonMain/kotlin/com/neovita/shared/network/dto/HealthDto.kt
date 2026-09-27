package com.neovita.shared.network.dto

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

// Agregados diarios crudos medidos por el dispositivo. El servidor decide qué hacer con
// ellos (hoy: alimentar el Longevity Score) — el cliente no interpreta nada.
@Serializable
data class DailyHealthMetricDto(
    val date: String,                       // ISO-8601 "YYYY-MM-DD" (día local del dispositivo)
    val steps: Int? = null,
    val sleepMinutes: Int? = null,
    // Es la media del día (HeartRateRecord.BPM_AVG), no la frecuencia en reposo.
    val avgHeartRate: Int? = null
)

/**
 * Registro manual de Perfil > "Mis métricas". Cada campo es opcional: el servidor sólo toca
 * los que llegan, para no pisar lo que el dispositivo ya sincronizó ese día.
 */
@Serializable
data class ManualMetricsRequest(
    val date: String = "",                  // ISO-8601 "YYYY-MM-DD", día local del dispositivo
    val steps: Int? = null,
    val weightKg: Double? = null,
    val bloodPressureSys: Int? = null,
    val bloodPressureDia: Int? = null,
    val glucoseMgdl: Int? = null
)

/** Último valor conocido de cada métrica manual (puede venir de días distintos). */
@Serializable
data class ManualMetricsDto(
    val steps: Int? = null,
    val weightKg: Double? = null,
    val bloodPressureSys: Int? = null,
    val bloodPressureDia: Int? = null,
    val glucoseMgdl: Int? = null
)

@Serializable
data class HealthUploadRequest(val metrics: List<DailyHealthMetricDto> = emptyList())

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class HealthSummaryDto(
    val avgDailySteps: Int? = null,
    val avgSleepMinutes: Int? = null,
    val avgHeartRate: Int? = null,
    // Always encoded even at its default (0): the server's JSON config skips fields equal to
    // their declared default, so an all-empty summary would otherwise serialize as "{}" and
    // the client couldn't distinguish "no data yet" from "field missing".
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val daysWithData: Int = 0
)
