package com.neovita.shared.domain.repository

interface AnalyticsRepository {
    /** Fire-and-forget: callers don't branch on the result, a failed log shouldn't surface. */
    suspend fun logEvent(type: String)
}
