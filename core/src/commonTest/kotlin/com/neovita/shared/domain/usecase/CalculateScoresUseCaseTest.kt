package com.neovita.shared.domain.usecase

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
}
