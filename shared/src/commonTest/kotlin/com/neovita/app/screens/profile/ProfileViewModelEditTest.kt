package com.neovita.app.screens.profile

import com.neovita.shared.domain.model.Assessment
import com.neovita.shared.domain.repository.AssessmentRepository
import com.neovita.shared.domain.repository.ManualMetricsRepository
import com.neovita.shared.domain.repository.UserRepository
import com.neovita.shared.network.dto.ManualMetricsDto
import com.neovita.shared.network.dto.ManualMetricsRequest
import com.neovita.shared.network.dto.UserDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Cubre el goal "que mi papá pueda gestionar su propia información": editar nombre/edad desde
 * Perfil (antes solo se pedían una vez, al crear la cuenta) y reiniciar su propio historial de
 * evaluaciones (antes solo lo podía hacer alguien con acceso directo a la base de datos).
 */
class ProfileViewModelEditTest {

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private class FakeUsers(private val failUpdate: Boolean = false) : UserRepository {
        var updated: Pair<String?, Int?>? = null
        override suspend fun getMe() = Result.success(UserDto("u1", "Juan", "j@x.co", 72, "USER"))
        override suspend fun updateMe(name: String?, age: Int?): Result<UserDto> {
            updated = name to age
            return if (failUpdate) Result.failure(RuntimeException("sin red"))
            else Result.success(UserDto("u1", name ?: "Juan", "j@x.co", age ?: 72, "USER"))
        }
    }

    private class FakeMetrics : ManualMetricsRepository {
        override suspend fun save(req: ManualMetricsRequest) = Result.success(Unit)
        override suspend fun latest() = Result.success(ManualMetricsDto())
    }

    private class FakeAssessments(private val failReset: Boolean = false) : AssessmentRepository {
        var resetCalled = false
        override suspend fun saveAssessment(
            exerciseFrequency: String, exerciseType: String,
            sleepHours: String, sleepQuality: Int, mainGoal: String
        ): Result<Assessment> = error("not used here")
        override suspend fun getLatestAssessment(userId: String): Assessment? = null
        override suspend fun resetHistory(): Result<Unit> {
            resetCalled = true
            return if (failReset) Result.failure(RuntimeException("sin red")) else Result.success(Unit)
        }
    }

    // --- Editar Perfil ---

    @Test
    fun `starting the edit opens the dialog with the current name and age`() = runTest {
        val vm = ProfileViewModel(FakeUsers(), null, FakeMetrics(), FakeAssessments())

        vm.startEditProfile()

        val edit = vm.state.value.editProfile
        assertNotNull(edit)
        assertEquals("Juan", edit.name)
        assertEquals("72", edit.age)
    }

    @Test
    fun `an empty name is rejected without calling the server`() = runTest {
        val users = FakeUsers()
        val vm = ProfileViewModel(users, null, FakeMetrics(), FakeAssessments())
        vm.startEditProfile()
        vm.updateEditName("   ")

        vm.saveProfile()

        assertNull(users.updated)
        assertNotNull(vm.state.value.editProfile?.error)
    }

    @Test
    fun `age only accepts digits and rejects an unreasonable value`() = runTest {
        val vm = ProfileViewModel(FakeUsers(), null, FakeMetrics(), FakeAssessments())
        vm.startEditProfile()
        vm.updateEditAge("7a5")
        assertEquals("75", vm.state.value.editProfile?.age, "las letras se descartan al escribir")

        vm.updateEditAge("5")
        vm.saveProfile()

        assertNotNull(vm.state.value.editProfile?.error, "5 años no es una edad válida")
    }

    @Test
    fun `a confirmed save updates the displayed user and closes the dialog`() = runTest {
        val users = FakeUsers()
        val vm = ProfileViewModel(users, null, FakeMetrics(), FakeAssessments())
        vm.startEditProfile()
        vm.updateEditName("Juan Villada")
        vm.updateEditAge("73")

        vm.saveProfile()

        assertEquals("Juan Villada" to 73, users.updated)
        assertEquals("Juan Villada", vm.state.value.user?.name)
        assertEquals(73, vm.state.value.user?.age)
        assertNull(vm.state.value.editProfile, "el diálogo debe cerrarse al confirmar el servidor")
    }

    @Test
    fun `a failed save keeps the dialog open with an error`() = runTest {
        val vm = ProfileViewModel(FakeUsers(failUpdate = true), null, FakeMetrics(), FakeAssessments())
        vm.startEditProfile()
        vm.updateEditAge("73")

        vm.saveProfile()

        assertNotNull(vm.state.value.editProfile, "un fallo no debe cerrar el diálogo en silencio")
        assertNotNull(vm.state.value.editProfile?.error)
    }

    @Test
    fun `dismissing the dialog discards unsaved edits`() = runTest {
        val vm = ProfileViewModel(FakeUsers(), null, FakeMetrics(), FakeAssessments())
        vm.startEditProfile()
        vm.updateEditName("algo a medio escribir")

        vm.dismissEditProfile()

        assertNull(vm.state.value.editProfile)
        assertEquals("Juan", vm.state.value.user?.name, "cerrar sin guardar no debe tocar el servidor")
    }

    // --- Reiniciar historial ---

    @Test
    fun `requesting a reset opens the confirmation step`() = runTest {
        val vm = ProfileViewModel(FakeUsers(), null, FakeMetrics(), FakeAssessments())

        vm.requestResetHistory()

        assertEquals(ResetHistoryStep.CONFIRM, vm.state.value.resetHistoryStep)
    }

    @Test
    fun `confirming calls the server and ends on the done step`() = runTest {
        val assessments = FakeAssessments()
        val vm = ProfileViewModel(FakeUsers(), null, FakeMetrics(), assessments)
        vm.requestResetHistory()

        vm.confirmResetHistory()

        assertTrue(assessments.resetCalled)
        assertEquals(ResetHistoryStep.DONE, vm.state.value.resetHistoryStep)
    }

    @Test
    fun `a failed reset returns to confirm with an error instead of closing silently`() = runTest {
        val vm = ProfileViewModel(FakeUsers(), null, FakeMetrics(), FakeAssessments(failReset = true))
        vm.requestResetHistory()

        vm.confirmResetHistory()

        assertEquals(ResetHistoryStep.CONFIRM, vm.state.value.resetHistoryStep)
        assertNotNull(vm.state.value.resetHistoryError)
    }

    @Test
    fun `dismissing the reset dialog without confirming does not touch the server`() = runTest {
        val assessments = FakeAssessments()
        val vm = ProfileViewModel(FakeUsers(), null, FakeMetrics(), assessments)
        vm.requestResetHistory()

        vm.dismissResetHistory()

        assertFalse(assessments.resetCalled)
        assertNull(vm.state.value.resetHistoryStep)
    }
}
