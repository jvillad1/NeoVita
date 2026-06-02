package com.neovita.shared.network.dto

import kotlinx.serialization.Serializable

@Serializable data class AssessmentRequest(
    val exerciseFrequency: String, val exerciseType: String,
    val sleepHours: String, val sleepQuality: Int, val mainGoal: String
)

@Serializable data class PillarScoresDto(
    val overall: Int, val exercise: Int, val sleep: Int, val nutrition: Int
)

@Serializable data class AssessmentResponse(
    val id: String, val userId: String, val createdAt: Long,
    val exerciseFrequency: String, val exerciseType: String,
    val sleepHours: String, val sleepQuality: Int,
    val mainGoal: String, val scores: PillarScoresDto
)
