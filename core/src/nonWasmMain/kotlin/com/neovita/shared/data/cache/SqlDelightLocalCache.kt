package com.neovita.shared.data.cache

import com.neovita.shared.db.NeoVitaDatabase
import kotlinx.datetime.Clock

/**
 * SQLDelight-backed [LocalCache] for the android & ios targets. Lives in nonWasmMain
 * because SQLDelight is excluded from the wasmJs build.
 */
class SqlDelightLocalCache(private val db: NeoVitaDatabase) : LocalCache {

    override fun cacheAssessment(
        id: String,
        userId: String,
        createdAt: Long,
        exerciseFrequency: String,
        exerciseType: String,
        sleepHours: String,
        sleepQuality: Long,
        mainGoal: String,
        scoresJson: String,
    ) {
        db.neoVitaQueries.insertAssessment(
            id, userId, createdAt, exerciseFrequency, exerciseType,
            sleepHours, sleepQuality, mainGoal, scoresJson,
        )
    }

    override fun getCurrentPlan(userId: String): CachedPlan? =
        db.neoVitaQueries.getCurrentPlan(userId).executeAsOneOrNull()?.let {
            CachedPlan(it.id, it.userId, it.generatedAt, it.planJson)
        }

    override fun getMetrics(userId: String): CachedMetrics? =
        db.neoVitaQueries.getMetrics(userId).executeAsOneOrNull()?.let {
            CachedMetrics(it.steps, it.weightKg, it.bloodPressureSys, it.bloodPressureDia, it.glucoseMgdl)
        }

    override fun upsertMetrics(
        userId: String,
        steps: Long?,
        weightKg: Double?,
        bloodPressureSys: Long?,
        bloodPressureDia: Long?,
        glucoseMgdl: Long?,
    ) {
        db.neoVitaQueries.upsertMetrics(
            userId = userId,
            steps = steps,
            weightKg = weightKg,
            bloodPressureSys = bloodPressureSys,
            bloodPressureDia = bloodPressureDia,
            glucoseMgdl = glucoseMgdl,
            updatedAt = Clock.System.now().toEpochMilliseconds(),
        )
    }
}
