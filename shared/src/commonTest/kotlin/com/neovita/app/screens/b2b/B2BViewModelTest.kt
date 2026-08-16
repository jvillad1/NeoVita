package com.neovita.app.screens.b2b

import com.neovita.shared.domain.repository.TeamRepository
import com.neovita.shared.network.dto.PillarScoresDto
import com.neovita.shared.network.dto.TeamMemberDto
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
import kotlin.test.assertTrue

/**
 * El panel de empresa existía como maquetado sin datos. Estos tests fijan lo que un
 * empleador necesita que sea cierto al abrirlo.
 */
class B2BViewModelTest {

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private class FakeRepo(val resultado: Result<TeamResponse>) : TeamRepository {
        override suspend fun getTeam(): Result<TeamResponse> = resultado
    }

    private fun miembro(nombre: String, overall: Int?) = TeamMemberDto(
        userId = nombre, name = nombre, email = "$nombre@acme.co",
        scores = overall?.let { PillarScoresDto(overall = it, exercise = it, sleep = it, nutrition = it) }
    )

    @Test
    fun `the team arrives with its average`() = runTest {
        val vm = B2BViewModel(FakeRepo(Result.success(
            TeamResponse(listOf(miembro("ana", 80), miembro("beto", 60)), avgScore = 70)
        )))

        val s = vm.state.value
        assertEquals(2, s.members.size)
        assertEquals(70, s.avgScore)
        assertEquals(false, s.isLoading, "se quedó cargando para siempre")
    }

    @Test
    fun `members without an assessment are counted, not hidden`() = runTest {
        // Un empleador necesita ver a quién le falta responder: es su métrica de adopción.
        val vm = B2BViewModel(FakeRepo(Result.success(
            TeamResponse(listOf(miembro("ana", 80), miembro("beto", null), miembro("cami", null)))
        )))

        assertEquals(3, vm.state.value.members.size, "no se puede esconder a quien no respondió")
        assertEquals(2, vm.state.value.sinEvaluar)
    }

    @Test
    fun `an empty team is not an error`() = runTest {
        val vm = B2BViewModel(FakeRepo(Result.success(TeamResponse())))

        assertEquals(emptyList(), vm.state.value.members)
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
