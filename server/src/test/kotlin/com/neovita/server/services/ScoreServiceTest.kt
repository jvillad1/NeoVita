package com.neovita.server.services

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScoreServiceTest {
    @Test fun `max exercise score for daily workouts with strength`() {
        val scores = ScoreService.calculate(
            exerciseFrequency = "Todos los días",
            exerciseType = "Pesas o resistencia",
            sleepHours = "7-8 horas",
            sleepQuality = 9
        )
        assertTrue(scores.exercise >= 90)
        assertTrue(scores.sleep >= 85)
    }

    @Test fun `low score for no exercise and poor sleep`() {
        val scores = ScoreService.calculate(
            exerciseFrequency = "Nunca",
            exerciseType = "No hago ejercicio",
            sleepHours = "Menos de 5 horas",
            sleepQuality = 2
        )
        assertTrue(scores.exercise <= 15)
        assertTrue(scores.sleep <= 20)
        assertTrue(scores.overall < 50)
    }

    @Test fun `overall is weighted average of pillars`() {
        val scores = ScoreService.calculate("2-3 veces", "Cardio", "6-8 horas", 6)
        val expectedOverall = (scores.exercise + scores.sleep + scores.nutrition) / 3
        assertEquals(expectedOverall, scores.overall)
    }
}
