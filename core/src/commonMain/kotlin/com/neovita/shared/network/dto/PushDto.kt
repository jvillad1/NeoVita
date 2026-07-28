package com.neovita.shared.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class RegisterDeviceRequest(val token: String, val platform: String)

@Serializable
data class PushSendRequest(
    val title: String,
    val body: String,
    val target: String? = null,   // "/web/x" o "https://…" — abre WebContentScreen al tocar
    val userId: String? = null    // null = todos los dispositivos registrados
)

@Serializable
data class PushSendResponse(val sent: Int)
