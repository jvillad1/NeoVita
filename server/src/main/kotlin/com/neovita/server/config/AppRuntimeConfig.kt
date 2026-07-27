package com.neovita.server.config

import com.neovita.shared.network.dto.FirebaseClientConfig

// Runtime app config sourced from env vars (see application.conf `appConfig` block).
// Changing any of these is a Railway env edit + restart — never a client release.
data class AppRuntimeConfig(
    val features: Map<String, Boolean>,
    val minVersionAndroid: Int,
    val minVersionIos: Int,
    val maintenance: Boolean,
    val firebase: FirebaseClientConfig?
)

// "chat=true, healthSync=false" → {chat=true, healthSync=false}; malformed entries dropped.
fun parseFeatures(raw: String): Map<String, Boolean> =
    raw.split(',').mapNotNull { entry ->
        val parts = entry.split('=', limit = 2)
        if (parts.size != 2) return@mapNotNull null
        val key = parts[0].trim()
        val value = parts[1].trim().lowercase()
        if (key.isEmpty() || value !in setOf("true", "false")) null
        else key to (value == "true")
    }.toMap()

// All four values or nothing: a partial Firebase config would fail at runtime on devices.
fun firebaseConfigFrom(apiKey: String?, appId: String?, projectId: String?, senderId: String?): FirebaseClientConfig? =
    if (apiKey.isNullOrBlank() || appId.isNullOrBlank() || projectId.isNullOrBlank() || senderId.isNullOrBlank()) null
    else FirebaseClientConfig(apiKey.trim(), appId.trim(), projectId.trim(), senderId.trim())
