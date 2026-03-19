package com.neovita.shared.domain.model

enum class UserRole { USER, EMPLOYER }

data class User(
    val id: String, val name: String, val email: String,
    val age: Int, val role: UserRole, val companyId: String?
)
