package com.neovita.shared.network.dto

import kotlinx.serialization.Serializable

@Serializable data class ApiError(val code: String, val message: String)
