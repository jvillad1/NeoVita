package com.neovita.shared.network.dto

import kotlinx.serialization.Serializable

// Public (non-secret) runtime config the server exposes to web clients.
@Serializable
data class WebConfigResponse(val googleClientId: String? = null)
