package com.neovita.shared.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class RegisterDeviceRequest(val token: String, val platform: String)
