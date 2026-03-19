package com.neovita.shared.data.repository

import com.neovita.shared.db.NeoVitaDatabase
import com.neovita.shared.domain.model.LongevityPlan
import com.neovita.shared.domain.model.PillarScores
import com.neovita.shared.domain.repository.PlanRepository
import com.neovita.shared.network.ApiService
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.*

class PlanRepositoryImpl(
    private val apiService: ApiService,
    private val db: NeoVitaDatabase
) : PlanRepository {

    override suspend fun getCurrent(): Result<LongevityPlan?> = runCatching {
        val plan = db.neoVitaDatabaseQueries.getCurrentPlan(
            // userId would come from session — simplified for MVP
            ""
        ).executeAsOneOrNull() ?: return@runCatching null
        parsePlan(plan.id, plan.userId, plan.generatedAt, plan.planJson)
    }

    override fun streamGenerate(): Flow<String> = apiService.streamPlanGeneration()

    private fun parsePlan(id: String, userId: String, generatedAt: Long, planJson: String): LongevityPlan? {
        return runCatching {
            val json = Json.parseToJsonElement(planJson).jsonObject
            LongevityPlan(
                id = id, userId = userId, generatedAt = generatedAt,
                nutrition = json["nutrition"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                sleep = json["sleep"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                exercise = json["exercise"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                scores = PillarScores(0, 0, 0, 0)
            )
        }.getOrNull()
    }
}
