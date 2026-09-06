package com.neovita.shared.network.dto

import kotlinx.serialization.Serializable

@Serializable data class UserDto(
    val id: String, val name: String, val email: String,
    val age: Int, val role: String, val companyId: String? = null,
    // Platform-wide content/screens backoffice — independent of `role`. An EMPLOYER (a
    // company's team lead) does not get this for free; see server's requireContentAdmin().
    val isContentAdmin: Boolean = false
)

@Serializable data class PatchUserRequest(val name: String? = null, val age: Int? = null)
