package com.neovita.shared.network.dto

import kotlinx.serialization.Serializable

/**
 * Modelo de cable del panel de empresa.
 *
 * Los puntajes salen de datos de salud (evaluación + wearables), que en Colombia son datos
 * sensibles (Ley 1581 de 2012). Por eso la empresa sólo recibe agregados del equipo, nunca
 * nombres, correos ni puntajes por persona — y los promedios sólo cuando han respondido al
 * menos [MIN_GROUP_SIZE] personas, para que no se pueda leer el puntaje de nadie en ellos.
 *
 * Como todos los DTO del proyecto, cada campo lleva valor por defecto para que una app ya
 * instalada siga parseando cuando el servidor añada campos.
 */
@Serializable
data class TeamResponse(
    /** Empleados de la empresa, sin contar a quienes tienen rol de empleador. */
    val totalMembers: Int = 0,
    /** Cuántos de ellos tienen al menos una evaluación. */
    val evaluatedCount: Int = 0,
    /** Promedios del equipo; null mientras [evaluatedCount] < [minGroupSize]. */
    val averages: PillarScoresDto? = null,
    val minGroupSize: Int = MIN_GROUP_SIZE
) {
    companion object {
        const val MIN_GROUP_SIZE = 5
    }
}
