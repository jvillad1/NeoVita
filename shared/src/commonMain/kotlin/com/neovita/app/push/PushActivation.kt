package com.neovita.app.push

import com.neovita.shared.network.ApiService
import com.neovita.shared.network.dto.WebConfigResponse

// Runtime push activation: called whenever remote config changes. Idempotent. Activates
// ONLY when the server serves a Firebase client config AND the "push" flag (default off)
// is on — the binary ships dormant and Railway env vars light it up, no release needed.
expect fun activatePush(config: WebConfigResponse?, apiService: ApiService)
