package com.neovita.shared.config

import com.neovita.shared.network.dto.WebConfigResponse

enum class AppPlatform { ANDROID, IOS, WEB }

data class ClientInfo(val platform: AppPlatform, val versionCode: Int)

enum class GateState { NORMAL, UPDATE_REQUIRED, MAINTENANCE }

// Pure gating decision. Safe by default: no config (fetch failed / cold start) never
// gates; only an explicit server statement does. Maintenance outranks the version gate.
// The web target is always the latest deploy, so it is never version-gated.
fun evaluateGate(config: WebConfigResponse?, client: ClientInfo): GateState {
    if (config == null) return GateState.NORMAL
    if (config.maintenance) return GateState.MAINTENANCE
    val min = when (client.platform) {
        AppPlatform.ANDROID -> config.minVersion.android
        AppPlatform.IOS -> config.minVersion.ios
        AppPlatform.WEB -> 0
    }
    return if (min > client.versionCode) GateState.UPDATE_REQUIRED else GateState.NORMAL
}

// `default` is per-feature: shipped features pass default = true (stay on when the server
// says nothing); dormant features pass default = false (off until the server enables them).
fun WebConfigResponse?.isFeatureEnabled(key: String, default: Boolean): Boolean =
    this?.features?.get(key) ?: default
