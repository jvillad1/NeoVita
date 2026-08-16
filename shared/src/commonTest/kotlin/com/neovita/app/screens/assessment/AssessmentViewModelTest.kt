package com.neovita.app.screens.assessment

import com.neovita.shared.domain.model.Assessment
import com.neovita.shared.domain.model.PillarScores
import com.neovita.shared.domain.repository.AssessmentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Responder la ÚLTIMA pregunta dejaba la pantalla congelada: `answer()` sólo guardaba las
 * respuestas en el rama del `else`, así que en la quinta el estado se quedaba con cuatro y
 * la condición de navegación (`answers.size >= QUESTIONS.size`) no se cumplía nunca.
 *
 * La evaluación SÍ se guardaba en el servidor — sólo la interfaz se quedaba clavada, y el
 * usuario tenía que recargar para verse en el dashboard.
 */
class AssessmentViewModelTest {

    // El ViewModel lanza en Dispatchers.Main.immediate; sin esto los tests no tienen Main.
    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private class FakeRepo(
        val fail: Boolean = false,
        var recibidas: Map<String, String> = emptyMap()
    ) : AssessmentRepository {
        override suspend fun saveAssessment(
            exerciseFrequency: String, exerciseType: String,
            sleepHours: String, sleepQuality: Int, mainGoal: String
        ): Result<Assessment> {
            recibidas = mapOf(
                "exercise_frequency" to exerciseFrequency, "exercise_type" to exerciseType,
                "sleep_hours" to sleepHours, "sleep_quality" to sleepQuality.toString(),
                "main_goal" to mainGoal
            )
            return if (fail) Result.failure(RuntimeException("sin red"))
            else Result.success(assessmentDePrueba())
        }

        override suspend fun getLatestAssessment(userId: String): Assessment? = null
    }

    private fun responderTodo(vm: AssessmentViewModel) {
        vm.answer("exercise_frequency", "Todos los días")
        vm.answer("exercise_type", "Cardio (caminar, correr, ciclismo)")
        vm.answer("sleep_hours", "7-8 horas")
        vm.answer("sleep_quality", "8")
        vm.answer("main_goal", "Aumentar energía y vitalidad")
    }

    @Test
    fun `answering the last question finishes the assessment`() = runTest {
        val vm = AssessmentViewModel(FakeRepo())

        responderTodo(vm)

        val s = vm.state.value
        assertEquals(QUESTIONS.size, s.answers.size, "la última respuesta no llegó al estado")
        assertTrue(vm.isDone, "la pantalla nunca se entera de que terminó y se queda congelada")
    }

    @Test
    fun `every answer reaches the repository, including the last one`() = runTest {
        val repo = FakeRepo()

        responderTodo(AssessmentViewModel(repo))

        assertEquals("Aumentar energía y vitalidad", repo.recibidas["main_goal"])
        assertEquals("8", repo.recibidas["sleep_quality"])
    }

    @Test
    fun `a failed save reports the error instead of pretending it finished`() = runTest {
        val vm = AssessmentViewModel(FakeRepo(fail = true))

        responderTodo(vm)

        val s = vm.state.value
        assertTrue(s.error != null, "un guardado fallido tiene que decirlo")
        assertNull(s.completed, "no puede darse por completada si no se guardó")
    }

    @Test
    fun `going back returns to the previous question and keeps what was answered`() = runTest {
        val vm = AssessmentViewModel(FakeRepo())
        vm.answer("exercise_frequency", "Todos los días")
        vm.answer("exercise_type", "Yoga o pilates")

        vm.goBack()

        assertEquals(1, vm.state.value.currentQuestion, "no volvió a la pregunta anterior")
        assertEquals("Todos los días", vm.state.value.answers["exercise_frequency"])
    }

    @Test
    fun `going back on the first question does nothing`() = runTest {
        val vm = AssessmentViewModel(FakeRepo())

        vm.goBack()

        assertEquals(0, vm.state.value.currentQuestion)
    }
}

private fun assessmentDePrueba() = Assessment(
    id = "a1", userId = "u1", createdAt = 0L,
    exerciseFrequency = "Todos los días", exerciseType = "Cardio (caminar, correr, ciclismo)",
    sleepHours = "7-8 horas", sleepQuality = 8, mainGoal = "Aumentar energía y vitalidad",
    scores = PillarScores(overall = 80, exercise = 85, sleep = 78, nutrition = 75)
)
