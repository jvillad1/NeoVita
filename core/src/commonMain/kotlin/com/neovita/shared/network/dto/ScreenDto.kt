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
@Serializable data class ActionDto(val type: String, val target: String)  // NAVIGATE|OPEN_URL

object ScreenTaxonomy {
    val SECTION_TYPES = listOf("HERO_SCORE", "CARD_ROW", "CARD_LIST", "QUOTE_BANNER", "CONTENT_FEED")
    val ACTION_TYPES = listOf("NAVIGATE", "OPEN_URL")
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

private fun isValidAction(a: ActionDto): Boolean = when (a.type) {
    "NAVIGATE" -> a.target in ScreenTaxonomy.NAVIGATE_TARGETS
    "OPEN_URL" -> a.target.startsWith("https://")
    else -> false
}
