package com.neovita.shared.domain.repository

import com.neovita.shared.domain.model.Assessment

interface AssessmentRepository {
    suspend fun saveAssessment(
        exerciseFrequency: String, exerciseType: String,
        sleepHours: String, sleepQuality: Int, mainGoal: String
    ): Result<Assessment>
    suspend fun getLatestAssessment(userId: String): Assessment?
}
