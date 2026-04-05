package com.neovita.server.routes

import com.neovita.server.db.repositories.AssessmentEntity
import com.neovita.server.db.repositories.AssessmentRepository
import com.neovita.server.db.repositories.PlanRepository
import com.neovita.server.db.toDto
import com.neovita.server.services.ClaudeMessage
import com.neovita.server.services.ClaudeService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.planRoutes(
    claudeService: ClaudeService,
    assessmentRepo: AssessmentRepository,
    planRepo: PlanRepository
) {
    authenticate("jwt-auth") {
        get("/plans/current") {
            val userId = call.principal<UserIdPrincipal>()!!.name
            val plan = planRepo.findCurrent(userId)
                ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(plan.toDto())
        }
        post("/plans/generate") {
            val userId = call.principal<UserIdPrincipal>()!!.name
            val assessment = assessmentRepo.findLatest(userId)
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("code" to "NO_ASSESSMENT", "message" to "Completa la evaluación primero")
                )

            val prompt = buildPlanPrompt(assessment)
            call.response.header(HttpHeaders.ContentType, "text/event-stream")
            call.response.header(HttpHeaders.CacheControl, "no-cache")
            call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                val buffer = StringBuilder()
                try {
                    claudeService.streamChat(listOf(ClaudeMessage("user", prompt)))
                        .collect { chunk ->
                            buffer.append(chunk)
                            write("data: $chunk\n\n")
                            flush()
                        }
                    // Persist only when stream completed successfully
                    if (buffer.isNotEmpty()) planRepo.save(userId, assessment.scores, buffer.toString())
                    write("data: [DONE]\n\n")
                } catch (e: Exception) {
                    write("data: [ERROR]\n\n")
                } finally {
                    flush()
                }
            }
        }
    }
}

private fun buildPlanPrompt(a: AssessmentEntity) = """
    Genera un plan de longevidad estructurado en JSON con claves "nutrition", "sleep", "exercise".
    Cada clave debe tener una lista de 3 recomendaciones concretas y accionables.
    Perfil: ejercicio ${a.exerciseFrequency}, tipo ${a.exerciseType},
    sueño ${a.sleepHours}h (calidad ${a.sleepQuality}/10), objetivo: ${a.mainGoal}.
    Responde SOLO con el JSON, sin texto adicional.
""".trimIndent()
