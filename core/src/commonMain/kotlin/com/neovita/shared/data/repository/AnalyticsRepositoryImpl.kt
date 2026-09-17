package com.neovita.shared.data.repository

import com.neovita.shared.domain.repository.AnalyticsRepository
import com.neovita.shared.network.ApiService

class AnalyticsRepositoryImpl(private val apiService: ApiService) : AnalyticsRepository {
    override suspend fun logEvent(type: String) {
        apiService.logEvent(type)
    }
}
