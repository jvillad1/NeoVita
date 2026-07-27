package com.neovita.server.routes

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

// Server-rendered pages for the in-app WebView slots (SDUI OPEN_WEBVIEW). Deploying a
// new page here + pointing an SDUI card at it ships a "new screen" to installed apps
// with zero store releases. Public by design; pages needing the user read the
// Authorization header the WebView attaches on its initial request.
// NOTE: this demo only checks that the header is PRESENT. Any real /web page that returns
// user data must actually verify the JWT (same auth plugin/validation as the rest of the
// API) — never trust "Bearer " presence alone as proof of an authenticated user.
fun Route.webRoutes() {
    get("/web/demo") {
        val hasSession = call.request.headers[HttpHeaders.Authorization]
            ?.startsWith("Bearer ") == true
        val sessionLabel = if (hasSession) "Sesión: activa" else "Sesión: no detectada"
        call.respondText(
            """
            <!DOCTYPE html>
            <html lang="es"><head><meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>NeoVita — Demo</title>
            <style>
                body { font-family: system-ui, sans-serif; margin: 0; padding: 48px 24px;
                       background: #7A1F3D; color: #fff; text-align: center; }
                .card { background: #fff; color: #333; border-radius: 16px; padding: 32px 24px;
                        max-width: 420px; margin: 0 auto; }
                .badge { display: inline-block; margin-top: 16px; padding: 6px 14px;
                         border-radius: 999px; background: #F3E6EC; color: #7A1F3D; }
            </style></head>
            <body>
                <div class="card">
                    <h1>Hola desde la web 🎉</h1>
                    <p>Esta pantalla vive en el servidor de <strong>NeoVita</strong>:
                       se actualiza con un deploy, sin tocar las tiendas.</p>
                    <span class="badge">$sessionLabel</span>
                </div>
            </body></html>
            """.trimIndent(),
            ContentType.Text.Html
        )
    }
}
