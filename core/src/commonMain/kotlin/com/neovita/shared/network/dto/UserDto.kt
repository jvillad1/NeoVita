package com.neovita.shared.network.dto

import kotlinx.serialization.Serializable

@Serializable data class UserDto(
    val id: String, val name: String, val email: String,
    val age: Int, val role: String, val companyId: String? = null
)

@Serializable data class PatchUserRequest(val name: String? = null, val age: Int? = null)
