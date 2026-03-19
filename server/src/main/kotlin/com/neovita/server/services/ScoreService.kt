package com.neovita.server.services

import kotlinx.serialization.Serializable

@Serializable
data class PillarScores(val overall: Int, val exercise: Int, val sleep: Int, val nutrition: Int)

object ScoreService {
    fun calculate(
        exerciseFrequency: String,
        exerciseType: String,
        sleepHours: String,
        sleepQuality: Int
    ): PillarScores {
        val exerciseFreqScore = when (exerciseFrequency) {
            "Todos los días" -> 100
            "4-5 veces" -> 85
            "2-3 veces" -> 65
            "1 vez" -> 40
            else -> 10  // "Nunca"
        }
        val exerciseTypeBonus = when (exerciseType) {
            "Pesas o resistencia" -> 5
            "Yoga o pilates" -> 3
            else -> 0
        }
        val exerciseScore = (exerciseFreqScore + exerciseTypeBonus).coerceAtMost(100)

        val sleepHoursScore = when (sleepHours) {
            "7-8 horas", "8+" -> 90
            "6-7 horas", "6-8 horas" -> 70
            "5-6 horas" -> 45
            else -> 15  // "Menos de 5 horas"
        }
        val sleepQualityScore = ((sleepQuality.toFloat() / 10f) * 100).toInt()
        val sleepScore = ((sleepHoursScore + sleepQualityScore) / 2)

        // Nutrition is not assessed yet — default to 60 as neutral baseline for MVP
        val nutritionScore = 60

        val overall = (exerciseScore + sleepScore + nutritionScore) / 3
        return PillarScores(overall, exerciseScore, sleepScore, nutritionScore)
    }
}
