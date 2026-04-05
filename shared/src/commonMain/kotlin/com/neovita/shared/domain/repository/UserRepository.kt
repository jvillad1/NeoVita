package com.neovita.shared.domain.repository

import com.neovita.shared.network.dto.UserDto

interface UserRepository {
    suspend fun getMe(): Result<UserDto>
    suspend fun updateMe(name: String? = null, age: Int? = null): Result<UserDto>
}
