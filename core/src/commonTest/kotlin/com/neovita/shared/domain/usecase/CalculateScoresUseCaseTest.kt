package com.neovita.shared.domain.usecase

import com.neovita.shared.domain.model.HealthSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CalculateScoresUseCaseTest {
    private val useCase = CalculateScoresUseCase()

    @Test fun `high scores for ideal inputs`() {
        val scores = useCase("Todos los días", "Pesas o resistencia", "7-8 horas", 9)
        assertTrue(scores.exercise >= 90, "exercise was ${scores.exercise}")
        assertTrue(scores.sleep >= 80, "sleep was ${scores.sleep}")
        assertTrue(scores.overall >= 75, "overall was ${scores.overall}")
    }

    @Test fun `low scores for sedentary poor-sleep inputs`() {
        val scores = useCase("Nunca", "No hago ejercicio", "Menos de 5 horas", 2)
        assertTrue(scores.exercise <= 15)
        assertTrue(scores.sleep <= 25)
    }

    @Test fun `overall is integer average of all three pillars`() {
        val scores = useCase("2-3 veces", "Cardio", "6-8 horas", 6)
        assertEquals((scores.exercise + scores.sleep + scores.nutrition) / 3, scores.overall)
    }

    @Test fun `null health summary preserves questionnaire behaviour`() {
        val withoutArg = useCase("2-3 veces", "Cardio", "6-8 horas", 6)
        val withNull = useCase("2-3 veces", "Cardio", "6-8 horas", 6, health = null)
        assertEquals(withoutArg, withNull)
    }

    @Test fun `measured steps override a modest questionnaire exercise score`() {
        val declared = useCase("1 vez", "Cardio", "6-8 horas", 6)
        val measured = useCase("1 vez", "Cardio", "6-8 horas", 6,
            HealthSummary(avgDailySteps = 12000))
        assertTrue(measured.exercise > declared.exercise,
            "measured ${measured.exercise} should beat declared ${declared.exercise}")
        assertEquals(100, measured.exercise)
    }

    @Test fun `few measured steps lower an optimistic questionnaire score`() {
        val declared = useCase("Todos los días", "Pesas o resistencia", "6-8 horas", 6)
        val measured = useCase("Todos los días", "Pesas o resistencia", "6-8 horas", 6,
            HealthSummary(avgDailySteps = 1500))
        assertTrue(measured.exercise < declared.exercise)
    }

    @Test fun `measured sleep replaces declared hours but keeps quality`() {
        val eightHours = useCase("2-3 veces", "Cardio", "Menos de 5 horas", 8,
            HealthSummary(avgSleepMinutes = 480))
        val fourHours = useCase("2-3 veces", "Cardio", "7-8 horas", 8,
            HealthSummary(avgSleepMinutes = 240))
        assertTrue(eightHours.sleep > fourHours.sleep)
    }

    @Test fun `partial health summary only overrides the pillar it measures`() {
        val declared = useCase("Todos los días", "Cardio", "7-8 horas", 8)
        val stepsOnly = useCase("Todos los días", "Cardio", "7-8 horas", 8,
            HealthSummary(avgDailySteps = 9000))
        assertEquals(declared.sleep, stepsOnly.sleep)   // sueño sin medir → cuestionario
    }

    @Test fun `overall stays the average of the three pillars with health data`() {
        val s = useCase("2-3 veces", "Cardio", "6-8 horas", 6,
            HealthSummary(avgDailySteps = 8000, avgSleepMinutes = 430))
        assertEquals((s.exercise + s.sleep + s.nutrition) / 3, s.overall)
    }
}
