package com.neovita.shared.network.dto

import kotlinx.serialization.Serializable

// Public (non-secret) runtime config the server exposes to clients. Every field has a
// default: already-installed apps must keep parsing when the server adds fields.
@Serializable
data class WebConfigResponse(
    val googleClientId: String? = null,
    val features: Map<String, Boolean> = emptyMap(),
    val minVersion: MinVersions = MinVersions(),
    val maintenance: Boolean = false,
    val firebase: FirebaseClientConfig? = null
)

@Serializable
data class MinVersions(val android: Int = 0, val ios: Int = 0)

// Firebase *client* values (the same ones google-services.json bakes into every APK —
// public, not secrets). Served remotely so push can activate on installed apps with a
// Railway env change instead of a store release. Null = push stays dormant.
@Serializable
data class FirebaseClientConfig(
    val apiKey: String,
    val appId: String,
    val projectId: String,
    val senderId: String
)
