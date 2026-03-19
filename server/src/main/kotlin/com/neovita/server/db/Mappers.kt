package com.neovita.server.db

import com.neovita.server.db.repositories.AssessmentEntity
import com.neovita.server.db.repositories.PlanEntity
import com.neovita.server.db.repositories.UserEntity

fun UserEntity.toDto() = mapOf(
    "id" to id, "email" to email, "name" to name,
    "age" to age, "role" to role, "companyId" to companyId
)

fun AssessmentEntity.toDto() = mapOf(
    "id" to id, "userId" to userId, "createdAt" to createdAt,
    "exerciseFrequency" to exerciseFrequency, "exerciseType" to exerciseType,
    "sleepHours" to sleepHours, "sleepQuality" to sleepQuality,
    "mainGoal" to mainGoal,
    "scores" to mapOf(
        "overall" to scores.overall, "exercise" to scores.exercise,
        "sleep" to scores.sleep, "nutrition" to scores.nutrition
    )
)

fun PlanEntity.toDto() = mapOf(
    "id" to id, "userId" to userId, "generatedAt" to generatedAt,
    "planContent" to planContent,
    "scores" to mapOf(
        "overall" to scores.overall, "exercise" to scores.exercise,
        "sleep" to scores.sleep, "nutrition" to scores.nutrition
    )
)
