package com.neovita.shared.data.repository

import com.neovita.shared.domain.repository.UserRepository
import com.neovita.shared.network.ApiService
import com.neovita.shared.network.dto.UserDto

class UserRepositoryImpl(private val apiService: ApiService) : UserRepository {
    override suspend fun getMe(): Result<UserDto> = apiService.getMe()
    override suspend fun updateMe(name: String?, age: Int?): Result<UserDto> =
        apiService.patchMe(name, age)
}
