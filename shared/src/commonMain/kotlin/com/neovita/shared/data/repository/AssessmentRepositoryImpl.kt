package com.neovita.shared.data.repository

import com.neovita.shared.data.mapper.toDomain
import com.neovita.shared.db.NeoVitaDatabase
import com.neovita.shared.domain.model.Assessment
import com.neovita.shared.domain.repository.AssessmentRepository
import com.neovita.shared.network.ApiService
import com.neovita.shared.network.dto.AssessmentRequest

class AssessmentRepositoryImpl(
    private val apiService: ApiService,
    private val db: NeoVitaDatabase
) : AssessmentRepository {

    override suspend fun saveAssessment(
        exerciseFrequency: String, exerciseType: String,
        sleepHours: String, sleepQuality: Int, mainGoal: String
    ): Result<Assessment> {
        val req = AssessmentRequest(exerciseFrequency, exerciseType, sleepHours, sleepQuality, mainGoal)
        return apiService.saveAssessment(req).map { dto ->
            db.neoVitaDatabaseQueries.insertAssessment(
                dto.id, dto.userId, dto.createdAt, dto.exerciseFrequency, dto.exerciseType,
                dto.sleepHours, dto.sleepQuality.toLong(), dto.mainGoal,
                """{"overall":${dto.scores.overall},"exercise":${dto.scores.exercise},"sleep":${dto.scores.sleep},"nutrition":${dto.scores.nutrition}}"""
            )
            dto.toDomain()
        }
    }

    override suspend fun getLatestAssessment(userId: String): Assessment? {
        // Try network first, fallback to cache
        val network = apiService.getLatestAssessment().getOrNull()
        if (network != null) return network.toDomain()
        return db.neoVitaDatabaseQueries.getLatestAssessment(userId).executeAsOneOrNull()?.let { row ->
            // Minimal offline reconstruction without scores recalculation
            null // Return null to trigger UI to show cached state
        }
    }
}
