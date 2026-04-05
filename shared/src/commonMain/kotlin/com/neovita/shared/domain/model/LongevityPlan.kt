package com.neovita.shared.domain.model

data class LongevityPlan(
    val id: String, val userId: String, val generatedAt: Long,
    val nutrition: List<String>, val sleep: List<String>,
    val exercise: List<String>, val mentalHealth: List<String>,
    val scores: PillarScores
)
