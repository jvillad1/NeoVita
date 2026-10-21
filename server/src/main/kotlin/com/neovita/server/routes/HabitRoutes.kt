package com.neovita.server.routes

import com.neovita.server.db.repositories.EventRepository
import com.neovita.server.db.repositories.HabitRepository
import com.neovita.shared.network.dto.HabitStatusDto
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.LocalDate

// Ids fijos en el cliente (las 5 tarjetas de "Hábitos de Zonas Azules"), no datos del
// servidor: una entrada con un habitId que ya no existe en el cliente simplemente deja de
// mostrarse — no hay FK que mantener ni migración que correr si la lista cambia.
private val VALID_HABIT_IDS = setOf("meditation", "plant_based_diet", "walk_10k", "sleep_8h", "social_bonds")

fun Route.habitRoutes(repo: HabitRepository, eventRepo: EventRepository) {
    authenticate("jwt-auth") {
        get("/habits/today") {
            val userId = call.principal<UserIdPrincipal>()!!.name
            val today = LocalDate.now().toString()
            call.respond(
                HabitStatusDto(
                    date = today,
                    loggedHabitIds = repo.habitIdsFor(userId, today).toList(),
                    streak = repo.currentStreak(userId)
                )
            )
        }
        post("/habits/{habitId}/toggle") {
            val userId = call.principal<UserIdPrincipal>()!!.name
            val habitId = call.parameters["habitId"]
            if (habitId !in VALID_HABIT_IDS) {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("code" to "INVALID_HABIT", "message" to "Hábito desconocido: $habitId"))
            }
            val today = LocalDate.now().toString()
            val nowDone = repo.toggle(userId, habitId!!, today)
            if (nowDone) eventRepo.log(userId, "habit_logged")
            call.respond(
                HabitStatusDto(
                    date = today,
                    loggedHabitIds = repo.habitIdsFor(userId, today).toList(),
                    streak = repo.currentStreak(userId)
                )
            )
        }
    }
}
