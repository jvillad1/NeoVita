package com.neovita.server.config

import kotlin.test.Test
import kotlin.test.assertEquals

class AppRuntimeConfigTest {
    @Test fun `parses feature csv with spaces`() {
        assertEquals(
            mapOf("chat" to true, "healthSync" to false),
            parseFeatures("chat=true, healthSync=false")
        )
    }

    @Test fun `blank yields empty map`() {
        assertEquals(emptyMap(), parseFeatures(""))
        assertEquals(emptyMap(), parseFeatures("   "))
    }

    @Test fun `malformed entries are skipped`() {
        assertEquals(mapOf("a" to true), parseFeatures("a=true,garbage,b=,=false,c=yes"))
    }
}
