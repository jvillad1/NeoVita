package com.neovita.shared.domain.model

data class PillarScores(val overall: Int, val exercise: Int, val sleep: Int, val nutrition: Int)

data class Assessment(
    val id: String, val userId: String, val createdAt: Long,
    val exerciseFrequency: String, val exerciseType: String,
    val sleepHours: String,     // "4-6" | "6-8" | "8+"
    val sleepQuality: Int,      // 1-10
    val mainGoal: String, val scores: PillarScores
)
