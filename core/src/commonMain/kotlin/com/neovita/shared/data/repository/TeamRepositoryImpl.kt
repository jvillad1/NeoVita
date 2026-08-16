package com.neovita.shared.data.repository

import com.neovita.shared.domain.repository.TeamRepository
import com.neovita.shared.network.ApiService
import com.neovita.shared.network.dto.TeamResponse

class TeamRepositoryImpl(private val apiService: ApiService) : TeamRepository {
    override suspend fun getTeam(): Result<TeamResponse> = apiService.getTeam()
}
