package com.neovita.app.screens.dashboard

import com.neovita.shared.domain.model.LongevityPlan
import com.neovita.shared.domain.repository.AssessmentRepository
import com.neovita.shared.domain.repository.ContentRepository
import com.neovita.shared.domain.repository.HabitRepository
import com.neovita.shared.domain.repository.PlanRepository
import com.neovita.shared.domain.repository.UserRepository
import com.neovita.shared.network.ApiService
import com.neovita.shared.network.dto.ContentItemDto
import com.neovita.shared.network.dto.ContentRequest
import com.neovita.shared.network.dto.HabitStatusDto
import com.neovita.shared.network.dto.UserDto
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
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
 * La racha y el check de "hecho hoy" se arman en el dashboard: estos tests fijan que el tap
 * se sienta instantáneo (optimista) y que, si el servidor no confirma, el check vuelve a su
 * estado real en vez de dejar a la persona creyendo que marcó algo que no se guardó.
 */
class DashboardViewModelHabitsTest {

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    // El dashboard necesita un ApiService real (no es una interfaz) para loadScreen(); un
    // mock que todo lo manda a 404 basta — loadScreen() ya tolera no tener screenDef.
    private val apiService = ApiService(
        baseUrl = "http://localhost",
        httpClient = HttpClient(MockEngine { respond("Not Found", HttpStatusCode.NotFound) })
    )

    private class FakeUsers : UserRepository {
        override suspend fun getMe() = Result.success(UserDto("u1", "Juan", "j@x.co", 40, "USER"))
        override suspend fun updateMe(name: String?, age: Int?) = getMe()
    }

    private class FakePlans : PlanRepository {
        override suspend fun getCurrent(): Result<LongevityPlan?> = Result.success(null)
        override fun streamGenerate(): Flow<String> = emptyFlow()
    }

    private class FakeContent : ContentRepository {
        override suspend fun getContent(): Result<List<ContentItemDto>> = Result.success(emptyList())
        override suspend fun getAllContent(): Result<List<ContentItemDto>> = Result.success(emptyList())
        override suspend fun create(req: ContentRequest): Result<ContentItemDto> = error("not used")
        override suspend fun update(id: String, req: ContentRequest): Result<ContentItemDto> = error("not used")
        override suspend fun delete(id: String): Result<Unit> = error("not used")
    }

    private class FakeHabits(
        initial: HabitStatusDto = HabitStatusDto(),
        private val failToggle: Boolean = false,
        /** Si no es null, toggle() espera a que el test lo complete antes de responder —
         *  así se puede mandar un segundo tap mientras el primero sigue "en vuelo". */
        private val gate: CompletableDeferred<Unit>? = null
    ) : HabitRepository {
        var status = initial
        var toggledIds = mutableListOf<String>()
        override suspend fun getToday(): Result<HabitStatusDto> = Result.success(status)
        override suspend fun toggle(habitId: String): Result<HabitStatusDto> {
            toggledIds.add(habitId)
            gate?.await()
            if (failToggle) return Result.failure(RuntimeException("sin red"))
            status = if (habitId in status.loggedHabitIds) {
                status.copy(loggedHabitIds = status.loggedHabitIds - habitId, streak = 0)
            } else {
                status.copy(loggedHabitIds = status.loggedHabitIds + habitId, streak = status.streak + 1)
            }
            return Result.success(status)
        }
    }

    private fun vm(habits: HabitRepository) = DashboardViewModel(
        userRepo = FakeUsers(), planRepo = FakePlans(), contentRepo = FakeContent(),
        apiService = apiService, localCache = null, analyticsRepo = null, habitRepo = habits
    )

    @Test
    fun `loads today's habits and streak on start`() = runTest {
        val vm = vm(FakeHabits(HabitStatusDto(loggedHabitIds = listOf("meditation"), streak = 3)))

        val s = vm.state.value
        assertEquals(listOf("meditation"), s.habits.loggedHabitIds)
        assertEquals(3, s.habits.streak)
    }

    @Test
    fun `tapping a habit marks it and the server-confirmed streak lands in state`() = runTest {
        val vm = vm(FakeHabits())

        vm.toggleHabit("walk_10k")

        assertTrue("walk_10k" in vm.state.value.habits.loggedHabitIds)
        assertEquals(1, vm.state.value.habits.streak)
        assertTrue(vm.state.value.habitsUpdating.isEmpty(), "no debe quedar marcado como 'en vuelo' tras confirmar")
    }

    @Test
    fun `tapping an already-marked habit unmarks it`() = runTest {
        val vm = vm(FakeHabits(HabitStatusDto(loggedHabitIds = listOf("sleep_8h"), streak = 5)))

        vm.toggleHabit("sleep_8h")

        assertTrue("sleep_8h" !in vm.state.value.habits.loggedHabitIds)
    }

    @Test
    fun `a failed toggle reverts the optimistic mark instead of lying about it`() = runTest {
        val repo = FakeHabits(failToggle = true)
        val vm = vm(repo)

        vm.toggleHabit("social_bonds")

        assertTrue(
            "social_bonds" !in vm.state.value.habits.loggedHabitIds,
            "si el servidor no confirmó, no puede seguir mostrándose como hecho"
        )
        assertTrue(vm.state.value.habitsUpdating.isEmpty())
    }

    @Test
    fun `tapping the same habit twice while it is in flight only sends one request`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repo = FakeHabits(gate = gate)
        val vm = vm(repo)

        vm.toggleHabit("meditation")     // queda "en vuelo": toggle() está esperando el gate
        assertTrue("meditation" in vm.state.value.habitsUpdating)
        vm.toggleHabit("meditation")     // el segundo tap debe ignorarse, no encolar otro request
        gate.complete(Unit)

        assertEquals(1, repo.toggledIds.size, "un segundo tap mientras el primero sigue en vuelo no debe disparar otro request")
    }
}
