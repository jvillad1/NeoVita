package com.neovita.server.routes

import com.neovita.server.db.repositories.HealthRepository
import com.neovita.shared.network.dto.DailyHealthMetricDto
import com.neovita.shared.network.dto.HealthSummaryDto
import com.neovita.shared.network.dto.HealthUploadRequest
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.LocalDate
import java.time.format.DateTimeParseException

private val DATE_REGEX = Regex("""\d{4}-\d{2}-\d{2}""")

// Valida el body ANTES de tocar la base de datos: aceptar cualquier fecha (en particular una
// futura) rompería summary(), que ordena por date DESC y no tiene forma de borrar filas, así
// que una fecha absurda quedaría fijada como "la más reciente" para siempre.
private fun validate(metrics: List<DailyHealthMetricDto>): String? {
    if (metrics.size > 60) return "metrics no puede tener más de 60 entradas (tiene ${metrics.size})"

    val today = LocalDate.now()
    val minDate = today.minusDays(30)
    val maxDate = today.plusDays(1)

    metrics.forEach { m ->
        if (!DATE_REGEX.matches(m.date)) {
            return "date inválida: '${m.date}' no tiene el formato YYYY-MM-DD"
        }
        val parsed = try {
            LocalDate.parse(m.date)
        } catch (e: DateTimeParseException) {
            return "date inválida: '${m.date}' no es una fecha real"
        }
        if (parsed.isBefore(minDate) || parsed.isAfter(maxDate)) {
            return "date fuera de rango: '${m.date}' debe estar entre $minDate y $maxDate"
        }
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
