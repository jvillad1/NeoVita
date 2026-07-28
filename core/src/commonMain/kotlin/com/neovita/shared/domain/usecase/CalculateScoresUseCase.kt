package com.neovita.shared.domain.usecase

import com.neovita.shared.domain.model.HealthSummary
import com.neovita.shared.domain.model.PillarScores

class CalculateScoresUseCase {
    // [health] son datos medidos (Health Connect/HealthKit). Cuando existen, sustituyen a la
    // respuesta declarada del pilar correspondiente; con health = null el resultado es
    // idéntico al del cuestionario de siempre.
    operator fun invoke(
        exerciseFrequency: String, exerciseType: String,
        sleepHours: String, sleepQuality: Int,
        health: HealthSummary? = null
    ): PillarScores {
        val exerciseFreqScore = when (exerciseFrequency) {
            "Todos los días" -> 100; "4-5 veces" -> 85; "2-3 veces" -> 65
            "1 vez" -> 40; else -> 10
        }
        val exerciseTypeBonus = when (exerciseType) {
            "Pesas o resistencia" -> 5; "Yoga o pilates" -> 3; else -> 0
        }
        val declaredExercise = (exerciseFreqScore + exerciseTypeBonus).coerceAtMost(100)
        // 10.000 pasos/día = 100; escala lineal, con el bonus por tipo de ejercicio intacto.
        val exercise = health?.avgDailySteps?.let { steps ->
            ((steps * 100) / 10_000).coerceIn(0, 100 - exerciseTypeBonus) + exerciseTypeBonus
        } ?: declaredExercise

        val declaredSleepHoursScore = when (sleepHours) {
            "7-8 horas", "8+" -> 90; "6-7 horas", "6-8 horas" -> 70
            "5-6 horas" -> 45; else -> 15
        }
        // Mismo baremo que el cuestionario, pero con las horas realmente dormidas.
        val sleepHoursScore = health?.avgSleepMinutes?.let { minutes ->
            when {
                minutes >= 420 -> 90      // 7 h o más
                minutes >= 360 -> 70      // 6-7 h
                minutes >= 300 -> 45      // 5-6 h
                else -> 15
            }
        } ?: declaredSleepHoursScore
        val sleep = ((sleepHoursScore + (sleepQuality * 10)) / 2)

        val nutrition = 60  // Baseline — not assessed in MVP
        return PillarScores(
            overall = (exercise + sleep + nutrition) / 3,
            exercise = exercise, sleep = sleep, nutrition = nutrition
        )
    }
}
