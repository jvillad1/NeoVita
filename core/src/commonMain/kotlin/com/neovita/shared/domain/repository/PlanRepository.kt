package com.neovita.shared.domain.repository

import com.neovita.shared.domain.model.LongevityPlan
import kotlinx.coroutines.flow.Flow

interface PlanRepository {
    suspend fun getCurrent(): Result<LongevityPlan?>
    fun streamGenerate(): Flow<String>
}
