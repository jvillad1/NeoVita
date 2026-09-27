package com.neovita.app.screens.profile

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
 * "Guardar métricas" mostraba "✓ Guardado" aunque nada llegara a ningún lado (en la web ni
 * siquiera había dónde guardar). Ahora el mensaje sólo aparece si el servidor confirmó.
 */
class ProfileViewModelMetricsTest {

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private class FakeUsers : UserRepository {
        override suspend fun getMe() = Result.success(UserDto("u1", "Juan", "j@x.co", 72, "USER"))
        override suspend fun updateMe(name: String?, age: Int?) = getMe()
    }

    private class FakeMetrics(
        val failSave: Boolean = false,
        val stored: ManualMetricsDto = ManualMetricsDto()
    ) : ManualMetricsRepository {
        var sent: ManualMetricsRequest? = null
        override suspend fun save(req: ManualMetricsRequest): Result<Unit> {
            sent = req
            return if (failSave) Result.failure(RuntimeException("sin red")) else Result.success(Unit)
        }
        override suspend fun latest() = Result.success(stored)
    }

    @Test
    fun `a confirmed save shows Guardado and sends what was typed`() = runTest {
        val repo = FakeMetrics()
        val vm = ProfileViewModel(FakeUsers(), null, repo)
        vm.updateMetric(MetricField.WEIGHT, "72,5")

        vm.saveMetrics()

        val m = vm.state.value.metrics
        assertTrue(m.saved)
        assertFalse(m.saving)
        assertNull(m.error)
        assertEquals(72.5, repo.sent?.weightKg)
        assertTrue(repo.sent!!.date.matches(Regex("""\d{4}-\d{2}-\d{2}""")), "la fecha debe ser YYYY-MM-DD")
    }

    @Test
    fun `a failed save does not pretend it saved`() = runTest {
        val vm = ProfileViewModel(FakeUsers(), null, FakeMetrics(failSave = true))
        vm.updateMetric(MetricField.WEIGHT, "72.5")

        vm.saveMetrics()

        val m = vm.state.value.metrics
        assertFalse(m.saved, "no puede decir ✓ Guardado si el servidor no lo recibió")
        assertNotNull(m.error)
    }

    @Test
    fun `an invalid form reports the error without calling the server`() = runTest {
        val repo = FakeMetrics()
        val vm = ProfileViewModel(FakeUsers(), null, repo)
        vm.updateMetric(MetricField.BP_SYS, "120")

        vm.saveMetrics()

        assertNull(repo.sent)
        assertNotNull(vm.state.value.metrics.error)
    }

    @Test
    fun `the form opens with what the server already has`() = runTest {
        val vm = ProfileViewModel(FakeUsers(), null, FakeMetrics(stored = ManualMetricsDto(weightKg = 71.0, glucoseMgdl = 95)))

        val m = vm.state.value.metrics
        assertEquals("71.0", m.weightKg)
        assertEquals("95", m.glucoseMgdl)
        assertEquals("", m.steps)
    }

    @Test
    fun `editing a field clears the saved mark and the error`() = runTest {
        val vm = ProfileViewModel(FakeUsers(), null, FakeMetrics())
        vm.updateMetric(MetricField.WEIGHT, "72")
        vm.saveMetrics()
        assertTrue(vm.state.value.metrics.saved)

        vm.updateMetric(MetricField.WEIGHT, "73")

        assertFalse(vm.state.value.metrics.saved)
    }
}
