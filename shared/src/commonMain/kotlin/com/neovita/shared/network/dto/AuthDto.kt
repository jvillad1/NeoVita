package com.neovita.shared.network.dto

import kotlinx.serialization.Serializable

@Serializable data class GoogleAuthRequest(val idToken: String)
@Serializable data class AuthResponse(val token: String, val isNewUser: Boolean)
