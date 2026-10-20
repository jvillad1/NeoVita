package com.neovita.app.screens.b2b

import com.neovita.shared.domain.repository.TeamRepository
import com.neovita.shared.network.dto.PillarScoresDto
import com.neovita.shared.network.dto.TeamResponse
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
 * El panel de empresa sólo muestra agregados del equipo (Ley 1581: los puntajes son datos de
 * salud). Estos tests fijan lo que un empleador ve al abrirlo.
 */
class B2BViewModelTest {

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private class FakeRepo(val resultado: Result<TeamResponse>) : TeamRepository {
        override suspend fun getTeam(): Result<TeamResponse> = resultado
    }

    private val promedios = PillarScoresDto(overall = 70, exercise = 65, sleep = 72, nutrition = 74)

    @Test
    fun `the team arrives with its averages`() = runTest {
        val vm = B2BViewModel(FakeRepo(Result.success(
            TeamResponse(totalMembers = 8, evaluatedCount = 6, averages = promedios)
        )))

        val s = vm.state.value
        assertEquals(promedios, s.team.averages)
        assertEquals(2, s.sinEvaluar)
        assertEquals(false, s.faltanEvaluaciones)
        assertEquals(false, s.isLoading, "se quedó cargando para siempre")
    }

    @Test
    fun `below the minimum it says how many answers are missing instead of showing a number`() = runTest {
        val vm = B2BViewModel(FakeRepo(Result.success(
            TeamResponse(totalMembers = 8, evaluatedCount = 3, averages = null, minGroupSize = 5)
        )))

        val s = vm.state.value
        assertNull(s.team.averages)
        assertTrue(s.faltanEvaluaciones)
        assertEquals(2, s.evaluacionesQueFaltan)
    }

    @Test
    fun `an empty team is not an error`() = runTest {
        val vm = B2BViewModel(FakeRepo(Result.success(TeamResponse())))

        assertEquals(0, vm.state.value.team.totalMembers)
        assertEquals(null, vm.state.value.error, "una empresa sin miembros aún no es un fallo")
        assertEquals(false, vm.state.value.isLoading)
    }

    @Test
    fun `a 403 explains that the account has no company, instead of saying retry`() = runTest {
        val vm = B2BViewModel(FakeRepo(Result.failure(RuntimeException("HTTP 403 Forbidden"))))

        val error = vm.state.value.error
        assertTrue(error != null && "empresa" in error, "el mensaje no orienta: $error")
        assertEquals(false, vm.state.value.isLoading)
    }
}
