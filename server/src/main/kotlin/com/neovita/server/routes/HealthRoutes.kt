package com.neovita.server.routes

import com.neovita.server.db.repositories.HealthRepository
import com.neovita.shared.network.dto.DailyHealthMetricDto
import com.neovita.shared.network.dto.HealthSummaryDto
import com.neovita.shared.network.dto.HealthUploadRequest
import com.neovita.shared.network.dto.ManualMetricsRequest
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.LocalDate
import java.time.format.DateTimeParseException

private val DATE_REGEX = Regex("""\d{4}-\d{2}-\d{2}""")

// Misma ventana que el resto de rutas de salud: una fecha absurda (sobre todo futura) quedaría
// fijada como "la más reciente" para siempre porque no hay forma de borrar filas.
private fun dateError(date: String): String? {
    val today = LocalDate.now()
    val minDate = today.minusDays(30)
    val maxDate = today.plusDays(1)

    if (!DATE_REGEX.matches(date)) {
        return "date inválida: '$date' no tiene el formato YYYY-MM-DD"
    }
    val parsed = try {
        LocalDate.parse(date)
    } catch (e: DateTimeParseException) {
        return "date inválida: '$date' no es una fecha real"
    }
    if (parsed.isBefore(minDate) || parsed.isAfter(maxDate)) {
        return "date fuera de rango: '$date' debe estar entre $minDate y $maxDate"
    }
    return null
}

private fun validateManual(m: ManualMetricsRequest): String? {
    dateError(m.date)?.let { return it }
    if (m.steps == null && m.weightKg == null && m.bloodPressureSys == null &&
        m.bloodPressureDia == null && m.glucoseMgdl == null
    ) return "no hay ninguna métrica que guardar"
    m.steps?.let { if (it !in 0..200_000) return "steps fuera de rango: $it" }
    m.weightKg?.let { if (it !in 20.0..400.0) return "weightKg fuera de rango: $it" }
    m.bloodPressureSys?.let { if (it !in 50..300) return "bloodPressureSys fuera de rango: $it" }
    m.bloodPressureDia?.let { if (it !in 30..200) return "bloodPressureDia fuera de rango: $it" }
    m.glucoseMgdl?.let { if (it !in 20..800) return "glucoseMgdl fuera de rango: $it" }
    // Una presión a medias no significa nada: van las dos o ninguna.
    val sys = m.bloodPressureSys
    val dia = m.bloodPressureDia
    if ((sys == null) != (dia == null)) return "la presión necesita sistólica y diastólica"
    if (sys != null && dia != null && sys <= dia) return "la sistólica debe ser mayor que la diastólica"
    return null
}

// Valida el body ANTES de tocar la base de datos: aceptar cualquier fecha (en particular una
// futura) rompería summary(), que ordena por date DESC y no tiene forma de borrar filas, así
// que una fecha absurda quedaría fijada como "la más reciente" para siempre.
private fun validate(metrics: List<DailyHealthMetricDto>): String? {
    if (metrics.size > 60) return "metrics no puede tener más de 60 entradas (tiene ${metrics.size})"

    metrics.forEach { m ->
        dateError(m.date)?.let { return it }
        m.steps?.let { if (it !in 0..200_000) return "steps fuera de rango en '${m.date}': $it" }
        m.sleepMinutes?.let { if (it !in 0..1440) return "sleepMinutes fuera de rango en '${m.date}': $it" }
        m.avgHeartRate?.let { if (it !in 0..300) return "avgHeartRate fuera de rango en '${m.date}': $it" }
    }
    return null
}

fun Route.healthRoutes(repo: HealthRepository) {
    authenticate("jwt-auth") {
        post("/health/metrics") {
            val userId = call.principal<UserIdPrincipal>()!!.name
            val req = call.receive<HealthUploadRequest>()
            val error = validate(req.metrics)
            if (error != null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("code" to "INVALID_METRICS", "message" to error))
                return@post
            }
            repo.upsertAll(userId, req.metrics)
            call.respond(HttpStatusCode.NoContent)
        }
        post("/health/manual") {
            val userId = call.principal<UserIdPrincipal>()!!.name
            val req = call.receive<ManualMetricsRequest>()
            val error = validateManual(req)
            if (error != null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("code" to "INVALID_METRICS", "message" to error))
                return@post
            }
            repo.upsertManual(userId, req)
            call.respond(HttpStatusCode.NoContent)
        }
        get("/health/manual") {
            call.respond(repo.latestManual(call.principal<UserIdPrincipal>()!!.name))
        }
        get("/health/summary") {
            val userId = call.principal<UserIdPrincipal>()!!.name
            val s = repo.summary(userId)
            call.respond(
                HealthSummaryDto(
                    avgDailySteps = s.avgDailySteps,
                    avgSleepMinutes = s.avgSleepMinutes,
                    avgHeartRate = s.avgHeartRate,
                    daysWithData = repo.daysWithData(userId)
                )
            )
        }
    }
}
