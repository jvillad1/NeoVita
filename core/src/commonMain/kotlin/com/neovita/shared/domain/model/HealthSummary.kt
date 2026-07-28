package com.neovita.shared.domain.model

/**
 * Medias de los últimos días medidas por el dispositivo (Health Connect / HealthKit).
 * Todo es opcional: cada pilar cae al cuestionario cuando no hay dato medido.
 */
data class HealthSummary(
    val avgDailySteps: Int? = null,
    val avgSleepMinutes: Int? = null,
    val avgHeartRate: Int? = null
)
