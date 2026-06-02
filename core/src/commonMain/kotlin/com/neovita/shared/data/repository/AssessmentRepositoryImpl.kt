package com.neovita.shared.data.repository

import com.neovita.shared.data.cache.LocalCache
import com.neovita.shared.data.mapper.toDomain
import com.neovita.shared.domain.model.Assessment
import com.neovita.shared.domain.repository.AssessmentRepository
import com.neovita.shared.network.ApiService
import com.neovita.shared.network.dto.AssessmentRequest

class AssessmentRepositoryImpl(
    private val apiService: ApiService,
    private val cache: LocalCache?,
) : AssessmentRepository {

    override suspend fun saveAssessment(
        exerciseFrequency: String, exerciseType: String,
        sleepHours: String, sleepQuality: Int, mainGoal: String
    ): Result<Assessment> {
        val req = AssessmentRequest(exerciseFrequency, exerciseType, sleepHours, sleepQuality, mainGoal)
        return apiService.saveAssessment(req).map { dto ->
            cache?.cacheAssessment(
                dto.id, dto.userId, dto.createdAt, dto.exerciseFrequency, dto.exerciseType,
                dto.sleepHours, dto.sleepQuality.toLong(), dto.mainGoal,
                """{"overall":${dto.scores.overall},"exercise":${dto.scores.exercise},"sleep":${dto.scores.sleep},"nutrition":${dto.scores.nutrition}}"""
            )
            dto.toDomain()
        }
    }

    override suspend fun getLatestAssessment(userId: String): Assessment? {
        // Network only — the offline cache row was never reconstructed into a domain
        // model (it returned null), so behaviour is unchanged when no cache is present.
        return apiService.getLatestAssessment().getOrNull()?.toDomain()
    }
}
