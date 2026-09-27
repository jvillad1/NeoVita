package com.neovita.server.db.repositories

import com.neovita.server.db.tables.HealthMetricsTable
import com.neovita.shared.domain.model.HealthSummary
import com.neovita.shared.network.dto.DailyHealthMetricDto
import com.neovita.shared.network.dto.ManualMetricsDto
import com.neovita.shared.network.dto.ManualMetricsRequest
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDate

class HealthRepository {

    /** Un registro por usuario y día; re-subir el mismo día actualiza (el dispositivo re-envía días parciales). */
    fun upsertAll(userId: String, metrics: List<DailyHealthMetricDto>) = transaction {
        val now = System.currentTimeMillis()
        metrics.forEach { m ->
            val updated = HealthMetricsTable.update({
                (HealthMetricsTable.userId eq userId) and (HealthMetricsTable.date eq m.date)
            }) {
                it[steps] = m.steps
                it[sleepMinutes] = m.sleepMinutes
                it[avgHeartRate] = m.avgHeartRate
                it[updatedAt] = now
            }
            if (updated == 0) {
                HealthMetricsTable.insert {
                    it[HealthMetricsTable.userId] = userId
                    it[date] = m.date
                    it[steps] = m.steps
                    it[sleepMinutes] = m.sleepMinutes
                    it[avgHeartRate] = m.avgHeartRate
                    it[updatedAt] = now
                }
            }
        }
    }

    /**
     * Registro manual: un solo día, y sólo se escriben los campos que llegan. A diferencia de
     * [upsertAll] (donde el dispositivo manda el día completo y un null significa "sin dato"),
     * aquí un null significa "no lo toqué": no debe borrar el sueño o los pasos que el
     * dispositivo ya sincronizó ese mismo día.
     */
    fun upsertManual(userId: String, m: ManualMetricsRequest) = transaction {
        val now = System.currentTimeMillis()
        val updated = HealthMetricsTable.update({
            (HealthMetricsTable.userId eq userId) and (HealthMetricsTable.date eq m.date)
        }) {
            m.steps?.let { v -> it[steps] = v }
            m.weightKg?.let { v -> it[weightKg] = v }
            m.bloodPressureSys?.let { v -> it[bloodPressureSys] = v }
            m.bloodPressureDia?.let { v -> it[bloodPressureDia] = v }
            m.glucoseMgdl?.let { v -> it[glucoseMgdl] = v }
            it[updatedAt] = now
        }
        if (updated == 0) {
            HealthMetricsTable.insert {
                it[HealthMetricsTable.userId] = userId
                it[date] = m.date
                it[steps] = m.steps
                it[weightKg] = m.weightKg
                it[bloodPressureSys] = m.bloodPressureSys
                it[bloodPressureDia] = m.bloodPressureDia
                it[glucoseMgdl] = m.glucoseMgdl
                it[updatedAt] = now
            }
        }
    }

    /** Último valor no nulo de cada métrica manual, mirando los últimos 60 días registrados. */
    fun latestManual(userId: String): ManualMetricsDto = transaction {
        val rows = HealthMetricsTable.selectAll()
            .where { HealthMetricsTable.userId eq userId }
            .orderBy(HealthMetricsTable.date, SortOrder.DESC)
            .limit(60)
            .toList()
        ManualMetricsDto(
            steps = rows.firstNotNullOfOrNull { it[HealthMetricsTable.steps] },
            weightKg = rows.firstNotNullOfOrNull { it[HealthMetricsTable.weightKg] },
            bloodPressureSys = rows.firstNotNullOfOrNull { it[HealthMetricsTable.bloodPressureSys] },
            bloodPressureDia = rows.firstNotNullOfOrNull { it[HealthMetricsTable.bloodPressureDia] },
            glucoseMgdl = rows.firstNotNullOfOrNull { it[HealthMetricsTable.glucoseMgdl] }
        )
    }

    // Ventana de "reciente": datos viejos no deben seguir sobrescribiendo un cuestionario
    // reciente; sin datos recientes el score vuelve al cuestionario. `date` es un string ISO
    // "YYYY-MM-DD", así que la comparación lexicográfica con el corte equivale a comparar fechas.
    private fun recencyCutoff(): String = LocalDate.now().minusDays(14).toString()

    /** Medias de los [days] días más recientes (dentro de la ventana de reciente) con datos. */
    fun summary(userId: String, days: Int = 7): HealthSummary = transaction {
        val rows = HealthMetricsTable.selectAll()
            .where { (HealthMetricsTable.userId eq userId) and (HealthMetricsTable.date greaterEq recencyCutoff()) }
            .orderBy(HealthMetricsTable.date, SortOrder.DESC)
            .limit(days)
            .toList()
        fun avg(values: List<Int>): Int? = if (values.isEmpty()) null else values.sum() / values.size
        HealthSummary(
            avgDailySteps = avg(rows.mapNotNull { it[HealthMetricsTable.steps] }),
            avgSleepMinutes = avg(rows.mapNotNull { it[HealthMetricsTable.sleepMinutes] }),
            avgHeartRate = avg(rows.mapNotNull { it[HealthMetricsTable.avgHeartRate] })
        )
    }

    /** Resúmenes de varios usuarios en una consulta; misma ventana de recencia que summary(). */
    fun summariesFor(userIds: List<String>, days: Int = 7): Map<String, HealthSummary> {
        if (userIds.isEmpty()) return emptyMap()
        return transaction {
            HealthMetricsTable.selectAll()
                .where { (HealthMetricsTable.userId inList userIds) and (HealthMetricsTable.date greaterEq recencyCutoff()) }
                .orderBy(HealthMetricsTable.date, SortOrder.DESC)
                .groupBy { it[HealthMetricsTable.userId] }
                .mapValues { (_, rows) ->
                    val recent = rows.take(days)
                    fun avg(values: List<Int>): Int? = if (values.isEmpty()) null else values.sum() / values.size
                    HealthSummary(
                        avgDailySteps = avg(recent.mapNotNull { it[HealthMetricsTable.steps] }),
                        avgSleepMinutes = avg(recent.mapNotNull { it[HealthMetricsTable.sleepMinutes] }),
                        avgHeartRate = avg(recent.mapNotNull { it[HealthMetricsTable.avgHeartRate] })
                    )
                }
        }
    }

    fun daysWithData(userId: String, days: Int = 7): Int = transaction {
        HealthMetricsTable.selectAll()
            .where { (HealthMetricsTable.userId eq userId) and (HealthMetricsTable.date greaterEq recencyCutoff()) }
            .orderBy(HealthMetricsTable.date, SortOrder.DESC)
            .limit(days)
            .count().toInt()
    }
}
