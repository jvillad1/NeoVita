package com.neovita.shared.network.dto

import kotlinx.serialization.Serializable

// category: NUTRITION | EXERCISE | SLEEP | MENTAL_HEALTH | GENERAL
// type:     ARTICLE | TIP | VIDEO
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
