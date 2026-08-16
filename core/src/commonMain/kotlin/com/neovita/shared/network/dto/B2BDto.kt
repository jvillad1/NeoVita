package com.neovita.shared.network.dto

import kotlinx.serialization.Serializable

/**
 * Modelos de cable del panel de empresa. Vivían dentro de `B2BRoutes.kt`, en el módulo del
 * servidor, así que el cliente no podía deserializar la respuesta ni aunque la pidiera: por
 * eso el endpoint existía sin que nadie lo consumiera.
 *
 * Como todos los DTO del proyecto, cada campo nuevo debe llevar valor por defecto para que
 * una app ya instalada siga parseando cuando el servidor añada campos.
 */
@Serializable
data class TeamMemberDto(
    val userId: String,
    val name: String,
    val email: String,
    /** Null cuando el miembro aún no ha respondido ninguna evaluación. */
    val scores: PillarScoresDto? = null
)

@Serializable
data class TeamResponse(
    val team: List<TeamMemberDto> = emptyList(),
    val avgScore: Int = 0
)
