package com.neovita.shared.network.dto

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ScreenDtoTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun roundtrip_serializes_and_deserializes() {
        val def = ScreenDefinitionDto(
            slug = "dashboard", version = 3,
            sections = listOf(
                SectionDto(type = "CARD_ROW", title = "Experiencias", cards = listOf(
                    CardDto(title = "Yoga", subtitle = "Tulum", imageUrl = "https://x/y.jpg",
                        badge = "Desde \$25 USD", meta = "4.9",
                        action = ActionDto("NAVIGATE", "plan")),
                )),
                SectionDto(type = "QUOTE_BANNER", text = "Muévete cada día"),
            ),
        )
        val decoded = json.decodeFromString<ScreenDefinitionDto>(json.encodeToString(ScreenDefinitionDto.serializer(), def))
        assertEquals(def, decoded)
    }

    @Test
    fun unknown_section_type_deserializes_and_is_filtered() {
        val raw = """{"slug":"dashboard","version":1,"sections":[
            {"type":"HOLOGRAM_3D","title":"Futuro"},
            {"type":"QUOTE_BANNER","text":"hola"}]}"""
        val def = json.decodeFromString<ScreenDefinitionDto>(raw)
        assertEquals(2, def.sections.size)              // deserializa sin explotar
        val renderable = renderableSections(def)
        assertEquals(1, renderable.size)                // el desconocido se salta
        assertEquals("QUOTE_BANNER", renderable[0].type)
    }

    @Test
    fun invalid_actions_are_stripped_not_fatal() {
        val def = ScreenDefinitionDto("s", 1, listOf(
            SectionDto(type = "CARD_LIST", cards = listOf(
                CardDto(title = "a", action = ActionDto("NAVIGATE", "settings")),   // target fuera de lista
                CardDto(title = "b", action = ActionDto("EXPLODE", "x")),            // tipo desconocido
                CardDto(title = "c", action = ActionDto("OPEN_URL", "http://insecure")), // no https
                CardDto(title = "d", action = ActionDto("NAVIGATE", "plan")),        // válida
            )),
        ))
        val cards = renderableSections(def)[0].cards
        assertNull(cards[0].action); assertNull(cards[1].action); assertNull(cards[2].action)
        assertEquals(ActionDto("NAVIGATE", "plan"), cards[3].action)
    }

    @Test
    fun open_webview_actions_validate_https_and_same_origin_relative() {
        val def = ScreenDefinitionDto("s", 1, listOf(
            SectionDto(type = "CARD_LIST", cards = listOf(
                CardDto(title = "a", action = ActionDto("OPEN_WEBVIEW", "https://neovita.app/promo")), // válida
                CardDto(title = "b", action = ActionDto("OPEN_WEBVIEW", "/web/demo")),                  // válida (same-origin)
                CardDto(title = "c", action = ActionDto("OPEN_WEBVIEW", "//evil.com/x")),               // protocol-relative → fuera
                CardDto(title = "d", action = ActionDto("OPEN_WEBVIEW", "http://insecure")),            // http absoluto → fuera
                CardDto(title = "e", action = ActionDto("OPEN_WEBVIEW", "web/demo")),                   // relativa sin / → fuera
            )),
        ))
        val cards = renderableSections(def)[0].cards
        assertEquals("https://neovita.app/promo", cards[0].action?.target)
        assertEquals("/web/demo", cards[1].action?.target)
        assertNull(cards[2].action)
        assertNull(cards[3].action)
        assertNull(cards[4].action)
    }

    private fun card(title: String = "Card", action: ActionDto? = null) =
        CardDto(title = title, action = action)

    @Test fun `a well-formed screen validates`() {
        val sections = listOf(
            SectionDto(type = "HERO_SCORE"),
            SectionDto(type = "CARD_ROW", title = "Novedades", cards = listOf(
                card(action = ActionDto("OPEN_WEBVIEW", "/web/demo")),
                card(action = ActionDto("NAVIGATE", "plan")),
            )),
            SectionDto(type = "QUOTE_BANNER", text = "Hola"),
        )
        assertNull(validateScreenSections(sections))
    }

    @Test fun `an unknown section type is rejected`() {
        val error = validateScreenSections(listOf(SectionDto(type = "MYSTERY_MEAT")))
        assertNotNull(error)
        assertTrue(error.contains("MYSTERY_MEAT"), error)
    }

    @Test fun `an invalid action target is rejected`() {
        val error = validateScreenSections(listOf(
            SectionDto(type = "CARD_ROW", cards = listOf(card(action = ActionDto("OPEN_URL", "http://inseguro"))))
        ))
        assertNotNull(error)
    }

    @Test fun `a protocol-relative webview target is rejected`() {
        val error = validateScreenSections(listOf(
            SectionDto(type = "CARD_ROW", cards = listOf(card(action = ActionDto("OPEN_WEBVIEW", "//evil.example"))))
        ))
        assertNotNull(error)
    }

    @Test fun `a blank card title is rejected`() {
        val error = validateScreenSections(listOf(
            SectionDto(type = "CARD_ROW", cards = listOf(card(title = "   ")))
        ))
        assertNotNull(error)
    }

    @Test fun `an empty screen is rejected`() {
        assertNotNull(validateScreenSections(emptyList()))
    }

    @Test fun `too many sections are rejected`() {
        val many = List(21) { SectionDto(type = "HERO_SCORE") }
        assertNotNull(validateScreenSections(many))
    }

    @Test fun `too many cards in one section are rejected`() {
        val section = SectionDto(type = "CARD_ROW", cards = List(31) { card() })
        assertNotNull(validateScreenSections(listOf(section)))
    }

    @Test fun `an over-long title is rejected`() {
        val section = SectionDto(type = "CARD_ROW", title = "x".repeat(201), cards = listOf(card()))
        assertNotNull(validateScreenSections(listOf(section)))
    }
}
