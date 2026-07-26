package com.neovita.shared.network.dto

import kotlinx.serialization.Serializable

// Public (non-secret) runtime config the server exposes to clients. Every field has a
// default: already-installed apps must keep parsing when the server adds fields.
@Serializable
data class WebConfigResponse(
    val googleClientId: String? = null,
    val features: Map<String, Boolean> = emptyMap(),
    val minVersion: MinVersions = MinVersions(),
    val maintenance: Boolean = false
)

@Serializable
data class MinVersions(val android: Int = 0, val ios: Int = 0)
