package com.neovita.shared.network.dto

import kotlinx.serialization.Serializable

@Serializable data class ScreenDefinitionDto(
    val slug: String,            // "dashboard"
    val version: Int,
    val sections: List<SectionDto>,
)
@Serializable data class SectionDto(
    val type: String,            // HERO_SCORE | CARD_ROW | CARD_LIST | QUOTE_BANNER | CONTENT_FEED
    val title: String? = null,
    val cards: List<CardDto> = emptyList(),   // CARD_ROW / CARD_LIST
    val text: String? = null,                  // QUOTE_BANNER
    val category: String? = null,              // CONTENT_FEED: filtro opcional de content_items
)
@Serializable data class CardDto(
    val title: String,
    val subtitle: String? = null,
    val imageUrl: String? = null,
    val badge: String? = null,
    val meta: String? = null,
    val action: ActionDto? = null,
)
@Serializable data class ActionDto(val type: String, val target: String)  // NAVIGATE|OPEN_URL|OPEN_WEBVIEW
@Serializable data class ScreenUpdateRequest(val sections: List<SectionDto> = emptyList())

object ScreenTaxonomy {
    val SECTION_TYPES = listOf("HERO_SCORE", "CARD_ROW", "CARD_LIST", "QUOTE_BANNER", "CONTENT_FEED")
    val ACTION_TYPES = listOf("NAVIGATE", "OPEN_URL", "OPEN_WEBVIEW")
    val NAVIGATE_TARGETS = listOf("home", "chat", "plan", "profile")
}

/** Secciones que un renderer de esta versión sabe dibujar, con acciones inválidas strippeadas. */
fun renderableSections(def: ScreenDefinitionDto): List<SectionDto> =
    def.sections
        .filter { it.type in ScreenTaxonomy.SECTION_TYPES }
        .map { section ->
            section.copy(cards = section.cards.map { card ->
                if (card.action != null && !isValidAction(card.action)) card.copy(action = null) else card
            })
        }

fun isValidAction(a: ActionDto): Boolean = when (a.type) {
    "NAVIGATE" -> a.target in ScreenTaxonomy.NAVIGATE_TARGETS
    "OPEN_URL" -> a.target.startsWith("https://")
    // In-app WebView: https absoluto, o ruta same-origin ("/x" pero nunca "//host", que
    // resolvería a un origen externo).
    "OPEN_WEBVIEW" -> a.target.startsWith("https://") ||
        (a.target.startsWith("/") && !a.target.startsWith("//"))
    else -> false
}

// Límites de una definición de pantalla. Un renderer viejo tolera lo que no conoce
// (renderableSections lo strippea), pero GUARDAR algo que ningún cliente sabe dibujar
// es un error del editor, no del cliente: por eso escribir es estricto y leer es laxo.
private const val MAX_SECTIONS = 20
private const val MAX_CARDS_PER_SECTION = 30
private const val MAX_TEXT = 200

/** Valida una definición completa. Devuelve null si es válida, o el motivo en español. */
fun validateScreenSections(sections: List<SectionDto>): String? {
    if (sections.isEmpty()) return "La pantalla debe tener al menos una sección"
    if (sections.size > MAX_SECTIONS) return "Demasiadas secciones (máximo $MAX_SECTIONS)"

    sections.forEachIndexed { index, section ->
        val where = "sección ${index + 1}"
        if (section.type !in ScreenTaxonomy.SECTION_TYPES) {
            return "Tipo de sección desconocido en $where: ${section.type}"
        }
        section.title?.let {
            if (it.length > MAX_TEXT) return "El título de $where supera $MAX_TEXT caracteres"
        }
        section.text?.let {
            if (it.length > MAX_TEXT) return "El texto de $where supera $MAX_TEXT caracteres"
        }
        section.category?.let {
            if (it.length > MAX_TEXT) return "La categoría de $where supera $MAX_TEXT caracteres"
        }
        if (section.cards.size > MAX_CARDS_PER_SECTION) {
            return "Demasiadas tarjetas en $where (máximo $MAX_CARDS_PER_SECTION)"
        }
        section.cards.forEachIndexed { cardIndex, card ->
            val cardWhere = "tarjeta ${cardIndex + 1} de $where"
            if (card.title.isBlank()) return "La $cardWhere no tiene título"
            if (card.title.length > MAX_TEXT) return "El título de la $cardWhere supera $MAX_TEXT caracteres"
            card.subtitle?.let {
                if (it.length > MAX_TEXT) return "El subtítulo de la $cardWhere supera $MAX_TEXT caracteres"
            }
            card.badge?.let {
                if (it.length > MAX_TEXT) return "El badge de la $cardWhere supera $MAX_TEXT caracteres"
            }
            card.meta?.let {
                if (it.length > MAX_TEXT) return "El meta de la $cardWhere supera $MAX_TEXT caracteres"
            }
            // La imagen la descarga el dispositivo de CADA usuaria, así que una URL a un host
            // arbitrario es un pixel de rastreo sobre toda la base — exigir https y validar
            // el esquema es lo mínimo.
            card.imageUrl?.let {
                if (it.isNotBlank() && !it.startsWith("https://")) {
                    return "La imagen de la $cardWhere debe usar https"
                }
            }
            card.action?.let { action ->
                if (!isValidAction(action)) {
                    return "Acción inválida en la $cardWhere: ${action.type} → ${action.target}"
                }
            }
        }
    }
    return null
}
