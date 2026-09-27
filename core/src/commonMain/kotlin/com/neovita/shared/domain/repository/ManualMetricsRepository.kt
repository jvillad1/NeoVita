package com.neovita.shared.domain.repository

import com.neovita.shared.network.dto.ManualMetricsDto
import com.neovita.shared.network.dto.ManualMetricsRequest

interface ManualMetricsRepository {
    suspend fun save(req: ManualMetricsRequest): Result<Unit>

    /** Último valor conocido de cada métrica, para rellenar el formulario al abrirlo. */
    suspend fun latest(): Result<ManualMetricsDto>
}
