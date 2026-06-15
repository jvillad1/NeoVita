package com.neovita.server.db

import com.neovita.server.db.repositories.AssessmentEntity
import com.neovita.server.db.repositories.PlanEntity
import com.neovita.server.db.repositories.UserEntity
import com.neovita.shared.network.dto.AssessmentResponse
import com.neovita.shared.network.dto.UserDto

// Server responses reuse :core's shared wire DTOs so client and server never drift.
fun UserEntity.toDto() = UserDto(
    id = id, name = name, email = email,
    age = age, role = role, companyId = companyId
)

fun AssessmentEntity.toDto() = AssessmentResponse(
    id = id, userId = userId, createdAt = createdAt,
    exerciseFrequency = exerciseFrequency, exerciseType = exerciseType,
    sleepHours = sleepHours, sleepQuality = sleepQuality,
    mainGoal = mainGoal, scores = scores
)

// No shared Plan DTO yet (the KMP client reads plans from its local cache, not
// over the wire), so this stays an ad-hoc map shaped for the server response.
fun PlanEntity.toDto() = mapOf(
    "id" to id, "userId" to userId, "generatedAt" to generatedAt,
    "planContent" to planContent,
    "scores" to mapOf(
        "overall" to scores.overall, "exercise" to scores.exercise,
        "sleep" to scores.sleep, "nutrition" to scores.nutrition
    )
)
