package com.neovita.shared.data.repository

import com.neovita.shared.domain.repository.HabitRepository
import com.neovita.shared.network.ApiService
import com.neovita.shared.network.dto.HabitStatusDto

class HabitRepositoryImpl(private val apiService: ApiService) : HabitRepository {
    override suspend fun getToday(): Result<HabitStatusDto> = apiService.getHabitsToday()
    override suspend fun toggle(habitId: String): Result<HabitStatusDto> = apiService.toggleHabit(habitId)
}
