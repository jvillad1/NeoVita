package com.neovita.server.config

import com.neovita.shared.network.dto.FirebaseClientConfig
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

    @Test fun `firebase config requires all four values`() {
        assertEquals(
            FirebaseClientConfig("k", "a", "p", "s"),
            firebaseConfigFrom("k", "a", "p", "s")
        )
        assertEquals(null, firebaseConfigFrom(null, "a", "p", "s"))
        assertEquals(null, firebaseConfigFrom("k", "", "p", "s"))
        assertEquals(null, firebaseConfigFrom("k", "a", "  ", "s"))
        assertEquals(null, firebaseConfigFrom(null, null, null, null))
    }
}
