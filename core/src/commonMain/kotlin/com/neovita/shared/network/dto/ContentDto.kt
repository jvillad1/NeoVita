package com.neovita.shared.network.dto

import kotlinx.serialization.Serializable

/** Allowed content taxonomy — single source of truth shared by server (validation)
 *  and client (admin form dropdowns). Keep in sync with the UI's ContentCategory/Type enums. */
object ContentTaxonomy {
    val CATEGORIES = listOf("NUTRITION", "EXERCISE", "SLEEP", "MENTAL_HEALTH", "GENERAL")
    val TYPES = listOf("ARTICLE", "TIP", "VIDEO")
}
@Serializable data class ContentItemDto(
    val id: String,
    val title: String,
    val category: String,
    val type: String,
    val teaser: String,
    val readMinutes: Int,
    val sortOrder: Int = 0,
    val active: Boolean = true,
)

@Serializable data class ContentRequest(
    val title: String,
    val category: String,
    val type: String,
    val teaser: String,
    val readMinutes: Int,
    val sortOrder: Int = 0,
    val active: Boolean = true,
)
