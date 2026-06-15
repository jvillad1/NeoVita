package com.neovita.shared.data.mapper

import com.neovita.shared.domain.model.Assessment
import com.neovita.shared.domain.model.PillarScores
import com.neovita.shared.network.dto.AssessmentResponse
import com.neovita.shared.network.dto.PillarScoresDto

fun AssessmentResponse.toDomain() = Assessment(
    id = id, userId = userId, createdAt = createdAt,
    exerciseFrequency = exerciseFrequency, exerciseType = exerciseType,
    sleepHours = sleepHours, sleepQuality = sleepQuality, mainGoal = mainGoal,
    scores = PillarScores(
        overall = scores.overall, exercise = scores.exercise,
        sleep = scores.sleep, nutrition = scores.nutrition
    )
)

fun PillarScores.toDto() = PillarScoresDto(
    overall = overall, exercise = exercise, sleep = sleep, nutrition = nutrition
)
