package com.neovita.app.health

import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregationResultGroupedByPeriod
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.neovita.app.auth.CurrentActivityHolder
import com.neovita.shared.network.ApiService
import com.neovita.shared.network.dto.DailyHealthMetricDto
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import kotlin.coroutines.resume

// No pedimos frecuencia cardíaca hasta que una función la use — pedir permisos de salud sin
// usarlos es una mala práctica de privacidad y Play lo revisa.
private val READ_PERMISSIONS = setOf(
    HealthPermission.getReadPermission(StepsRecord::class),
    HealthPermission.getReadPermission(SleepSessionRecord::class),
)

actual class HealthSyncClient actual constructor() {

    private fun client(): HealthConnectClient? {
        val context = CurrentActivityHolder.activity?.applicationContext ?: return null
        return runCatching {
            if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) null
            else HealthConnectClient.getOrCreate(context)
        }.getOrNull()
    }

    actual fun isAvailable(): Boolean = client() != null

    actual suspend fun requestPermissions(): Boolean {
        val client = client() ?: return false
        val granted = runCatching { client.permissionController.getGrantedPermissions() }
            .getOrDefault(emptySet())
        if (granted.containsAll(READ_PERMISSIONS)) return true
        val launcher = HealthPermissionLauncher.request ?: return false
        return runCatching {
            suspendCancellableCoroutine { cont ->
                launcher(READ_PERMISSIONS) { ok -> if (cont.isActive) cont.resume(ok) }
            }
        }.getOrElse {
            Log.w("NeoVitaHealth", "No se pudo solicitar permisos de salud", it)
            false
        }
    }

    // Lee los últimos 7 días agregados por día y los sube crudos: el servidor decide.
    actual suspend fun sync(apiService: ApiService): HealthSyncState {
        val client = client() ?: return HealthSyncState.UNAVAILABLE
        val granted = runCatching { client.permissionController.getGrantedPermissions() }
            .getOrDefault(emptySet())
        if (!granted.containsAll(READ_PERMISSIONS)) return HealthSyncState.NEEDS_PERMISSION

        return runCatching {
            // Anclado a medianoche: si no, los buckets diarios corren de hora-actual a
            // hora-actual, y re-sincronizar en otro momento del día reescribe las mismas
            // fechas con datos cortados de forma distinta.
            val start = LocalDate.now().minusDays(6).atStartOfDay()
            val end = LocalDateTime.now()
            val groups: List<AggregationResultGroupedByPeriod> = client.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    metrics = setOf(
                        StepsRecord.COUNT_TOTAL,
                        SleepSessionRecord.SLEEP_DURATION_TOTAL
                    ),
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    timeRangeSlicer = Period.ofDays(1)
                )
            )
            val metrics = groups.mapNotNull { group ->
                val steps = group.result[StepsRecord.COUNT_TOTAL]?.toInt()
                val sleep = group.result[SleepSessionRecord.SLEEP_DURATION_TOTAL]?.toMinutes()?.toInt()
                if (steps == null && sleep == null) null
                else DailyHealthMetricDto(
                    date = group.startTime.toLocalDate().toString(),
                    steps = steps, sleepMinutes = sleep
                )
            }
            if (metrics.isEmpty()) return HealthSyncState.SYNCED   // sin datos que subir, no es error
            apiService.uploadHealthMetrics(metrics)
                .fold(onSuccess = { HealthSyncState.SYNCED }, onFailure = { HealthSyncState.ERROR })
        }.getOrElse {
            Log.w("NeoVitaHealth", "Sync de salud falló", it)
            HealthSyncState.ERROR
        }
    }
}
