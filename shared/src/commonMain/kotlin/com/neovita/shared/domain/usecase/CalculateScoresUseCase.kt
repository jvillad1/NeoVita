package com.neovita.shared.domain.usecase

import com.neovita.shared.domain.model.PillarScores

class CalculateScoresUseCase {
    operator fun invoke(
        exerciseFrequency: String, exerciseType: String,
        sleepHours: String, sleepQuality: Int
    ): PillarScores {
        val exerciseFreqScore = when (exerciseFrequency) {
            "Todos los días" -> 100; "4-5 veces" -> 85; "2-3 veces" -> 65
            "1 vez" -> 40; else -> 10
        }
        val exerciseTypeBonus = if (exerciseType == "Pesas o resistencia") 5 else 0
        val exercise = (exerciseFreqScore + exerciseTypeBonus).coerceAtMost(100)

        val sleepHoursScore = when (sleepHours) {
            "7-8 horas", "8+" -> 90; "6-7 horas", "6-8 horas" -> 70
            "5-6 horas" -> 45; else -> 15
        }
        val sleep = ((sleepHoursScore + (sleepQuality * 10)) / 2)
        val nutrition = 60  // Baseline — not assessed in MVP
        return PillarScores(
            overall = (exercise + sleep + nutrition) / 3,
            exercise = exercise, sleep = sleep, nutrition = nutrition
        )
    }
}
