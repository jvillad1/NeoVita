package com.neovita.shared.domain.repository

import com.neovita.shared.network.dto.HabitStatusDto

interface HabitRepository {
    suspend fun getToday(): Result<HabitStatusDto>
    suspend fun toggle(habitId: String): Result<HabitStatusDto>
}
