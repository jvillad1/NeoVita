package com.neovita.shared.domain.repository

import com.neovita.shared.network.dto.TeamResponse

interface TeamRepository {
    suspend fun getTeam(): Result<TeamResponse>
}
