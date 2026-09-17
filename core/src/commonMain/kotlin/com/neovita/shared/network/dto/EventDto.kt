package com.neovita.shared.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class LogEventRequest(val type: String)
