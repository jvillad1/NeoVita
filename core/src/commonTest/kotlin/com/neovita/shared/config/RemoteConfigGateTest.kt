package com.neovita.shared.config

import com.neovita.shared.network.dto.MinVersions
import com.neovita.shared.network.dto.WebConfigResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemoteConfigGateTest {
    private val android5 = ClientInfo(AppPlatform.ANDROID, versionCode = 5)

    @Test fun `null config is safe - NORMAL`() {
        assertEquals(GateState.NORMAL, evaluateGate(null, android5))
    }

    @Test fun `maintenance wins over version gate`() {
        val cfg = WebConfigResponse(maintenance = true, minVersion = MinVersions(android = 99))
        assertEquals(GateState.MAINTENANCE, evaluateGate(cfg, android5))
    }

    @Test fun `older android build gets UPDATE_REQUIRED`() {
        val cfg = WebConfigResponse(minVersion = MinVersions(android = 6))
        assertEquals(GateState.UPDATE_REQUIRED, evaluateGate(cfg, android5))
    }

    @Test fun `equal version passes`() {
        val cfg = WebConfigResponse(minVersion = MinVersions(android = 5))
        assertEquals(GateState.NORMAL, evaluateGate(cfg, android5))
    }

    @Test fun `web is never version gated`() {
        val cfg = WebConfigResponse(minVersion = MinVersions(android = 99, ios = 99))
        assertEquals(GateState.NORMAL, evaluateGate(cfg, ClientInfo(AppPlatform.WEB, 0)))
    }

    @Test fun `ios gate uses the ios minimum`() {
        val cfg = WebConfigResponse(minVersion = MinVersions(ios = 3))
        assertEquals(GateState.UPDATE_REQUIRED, evaluateGate(cfg, ClientInfo(AppPlatform.IOS, 2)))
    }

    @Test fun `feature default applies when key absent or config null`() {
        val cfg = WebConfigResponse(features = mapOf("healthSync" to true))
        assertTrue(cfg.isFeatureEnabled("chat", default = true))
        assertFalse(cfg.isFeatureEnabled("newThing", default = false))
        assertTrue(cfg.isFeatureEnabled("healthSync", default = false))
        assertFalse((null as WebConfigResponse?).isFeatureEnabled("dormant", default = false))
        assertTrue((null as WebConfigResponse?).isFeatureEnabled("chat", default = true))
    }

    @Test fun `feature false hides a shipped feature`() {
        val cfg = WebConfigResponse(features = mapOf("chat" to false))
        assertFalse(cfg.isFeatureEnabled("chat", default = true))
    }
}
