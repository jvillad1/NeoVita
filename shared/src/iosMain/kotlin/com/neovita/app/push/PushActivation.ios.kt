package com.neovita.app.push

import com.neovita.shared.network.ApiService
import com.neovita.shared.network.dto.WebConfigResponse

actual fun activatePush(config: WebConfigResponse?, apiService: ApiService) {
    // Push aún no disponible en esta plataforma.
}
