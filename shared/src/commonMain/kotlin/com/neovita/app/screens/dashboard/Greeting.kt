package com.neovita.app.screens.dashboard

/**
 * Saludo del dashboard. Estaba escrito a mano como "Hola, Juan Guillermo", así que TODOS los
 * usuarios veían ese nombre — que además no coincidía con ninguna cuenta real.
 *
 * Se usa sólo el primer nombre: "Hola, Juan Camilo Villada" es un titular de 32sp que no cabe
 * y suena a carta del banco. Sin nombre se saluda a secas, que es preferible a inventarlo o a
 * dejar un hueco.
 */
fun saludoDashboard(nombreCompleto: String?): String {
    val primero = nombreCompleto?.trim()?.substringBefore(' ')?.takeIf { it.isNotBlank() }
    return if (primero == null) "Hola" else "Hola, $primero"
}
