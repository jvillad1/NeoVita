package com.neovita.shared.data.cache

/**
 * Platform-agnostic local cache contract. The SQLDelight-backed implementation lives in
 * the `nonWasmMain` source set (android + ios) because SQLDelight has no wasmJs artifact.
 * The web (wasmJs) build runs without a cache — callers receive `null` and skip caching.
 */
interface LocalCache {
    fun cacheAssessment(
        id: String,
        userId: String,
        createdAt: Long,
        exerciseFrequency: String,
        exerciseType: String,
        sleepHours: String,
        sleepQuality: Long,
        mainGoal: String,
        scoresJson: String,
    )

    fun getCurrentPlan(userId: String): CachedPlan?

    fun getMetrics(userId: String): CachedMetrics?

    fun upsertMetrics(
        userId: String,
        steps: Long?,
        weightKg: Double?,
        bloodPressureSys: Long?,
        bloodPressureDia: Long?,
        glucoseMgdl: Long?,
    )

    fun cacheScreen(slug: String, version: Int, json: String)

    fun getScreen(slug: String): CachedScreen?
}

data class CachedPlan(
    val id: String,
    val userId: String,
    val generatedAt: Long,
    val planJson: String,
)

data class CachedMetrics(
    val steps: Long?,
    val weightKg: Double?,
    val bloodPressureSys: Long?,
    val bloodPressureDia: Long?,
    val glucoseMgdl: Long?,
)

data class CachedScreen(
    val version: Int,
    val json: String,
)
