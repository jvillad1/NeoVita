package com.neovita.app.screens.dashboard

/**
 * Primer nombre de la persona, o null si no hay nombre utilizable.
 *
 * El saludo estuvo escrito a mano como "Hola, Juan Guillermo", así que TODOS los usuarios veían
 * ese nombre. Se usa sólo el primer nombre: "Juan Camilo Villada" es un titular que no cabe y
 * suena a carta del banco. Sin nombre se saluda a secas (ver saludoValle), que es preferible a
 * inventarlo o a dejar un hueco.
 */
fun primerNombre(nombreCompleto: String?): String? =
    nombreCompleto?.trim()?.substringBefore(' ')?.takeIf { it.isNotBlank() }
