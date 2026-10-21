package com.neovita.shared.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class HabitStatusDto(
    val date: String = "",
    val loggedHabitIds: List<String> = emptyList(),
    val streak: Int = 0
)
