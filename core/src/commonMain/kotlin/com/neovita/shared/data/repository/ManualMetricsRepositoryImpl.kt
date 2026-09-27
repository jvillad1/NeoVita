package com.neovita.shared.data.repository

import com.neovita.shared.domain.repository.ManualMetricsRepository
import com.neovita.shared.network.ApiService
import com.neovita.shared.network.dto.ManualMetricsDto
import com.neovita.shared.network.dto.ManualMetricsRequest

class ManualMetricsRepositoryImpl(private val apiService: ApiService) : ManualMetricsRepository {
    override suspend fun save(req: ManualMetricsRequest): Result<Unit> = apiService.saveManualMetrics(req)
    override suspend fun latest(): Result<ManualMetricsDto> = apiService.getManualMetrics()
}
