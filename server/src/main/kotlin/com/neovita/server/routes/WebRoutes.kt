package com.neovita.server.routes

import com.neovita.server.db.repositories.UserRepository
import com.neovita.server.plugins.requireContentAdmin
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

// Server-rendered pages for the in-app WebView slots (SDUI OPEN_WEBVIEW). Deploying a
// new page here + pointing an SDUI card at it ships a "new screen" to installed apps
// with zero store releases. Public by design; pages needing the user read the
// Authorization header the WebView attaches on its initial request.
// NOTE: this demo only checks that the header is PRESENT. Any real /web page that returns
// user data must actually verify the JWT (same auth plugin/validation as the rest of the
// API) — never trust "Bearer " presence alone as proof of an authenticated user.
fun Route.webRoutes(userRepository: UserRepository) {
    // Requerida por Google para publicar la pantalla de consentimiento de OAuth fuera de
    // modo Testing (Branding page: "homepage url" y "privacy policy url"). El contenido
    // describe únicamente lo que el código realmente hace — nada aspiracional.
    get("/legal/privacy") {
        call.respondText(PRIVACY_POLICY_HTML, ContentType.Text.Html)
    }

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

    // El editor de pantallas: HTML servido por nosotros, abierto desde Perfil con el slot
    // WebView. Vive en el servidor a propósito — mejorarlo es un deploy, nunca un release.
    authenticate("jwt-auth") {
        get("/web/admin/screens") {
            if (!call.requireContentAdmin(userRepository)) return@get
            // Los WebView sólo adjuntan el JWT a la petición INICIAL, así que los fetch()
            // de la página irían sin credencial. Le pasamos el token de quien ya se
            // autenticó aquí. Es su propio token, la página es same-origin, va por https y
            // está restringida a isContentAdmin; aun así no debe registrarse en logs ni
            // salir de este origen. (Mejora futura: un token efímero con alcance sólo-pantallas.)
            val token = call.request.headers[HttpHeaders.Authorization]
                ?.removePrefix("Bearer ")?.trim().orEmpty()
            val html = javaClass.getResource("/web/screen-editor.html")!!.readText()
                .replace("__BOOTSTRAP_TOKEN__", token)
            // La página lleva el JWT de quien la pidió: nunca debe quedar en la caché del WebView.
            call.response.header(HttpHeaders.CacheControl, "no-store")
            call.respondText(html, ContentType.Text.Html)
        }
    }
}

private val PRIVACY_POLICY_HTML = """
    <!DOCTYPE html>
    <html lang="es"><head><meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>NeoVita — Política de Privacidad</title>
    <style>
        body { font-family: system-ui, sans-serif; margin: 0; padding: 32px 20px 64px;
               background: #FAF7F8; color: #2A1620; line-height: 1.55; }
        .wrap { max-width: 640px; margin: 0 auto; }
        h1 { color: #7A1F3D; }
        h2 { color: #7A1F3D; margin-top: 2em; font-size: 1.1em; }
        code { background: #F3E6EC; padding: 1px 5px; border-radius: 4px; }
        .updated { color: #7A1F3D99; font-size: 0.9em; }
    </style></head>
    <body><div class="wrap">
        <h1>Política de Privacidad de NeoVita</h1>
        <p class="updated">Última actualización: septiembre de 2026</p>

        <p>NeoVita es una app de coaching de longevidad con inteligencia artificial. Esta
        página describe qué datos recolectamos y para qué los usamos.</p>

        <h2>Qué datos recolectamos</h2>
        <ul>
            <li><strong>Cuenta:</strong> nombre, correo y edad, obtenidos al iniciar sesión con
            tu cuenta de Google.</li>
            <li><strong>Evaluación de longevidad:</strong> tus respuestas sobre ejercicio,
            sueño y objetivos, usadas para calcular tu puntaje y tu plan.</li>
            <li><strong>Datos de salud (opcionales):</strong> si autorizas el acceso a Health
            Connect (Android) o HealthKit (iOS), leemos pasos, minutos de sueño y frecuencia
            cardíaca promedio para afinar tu puntaje con datos medidos en vez de declarados.</li>
            <li><strong>Conversaciones con el coach:</strong> los mensajes que le escribes al
            coach de IA se envían a la API de Anthropic (Claude) para generar la respuesta.
            NeoVita no guarda el historial de esas conversaciones en su servidor.</li>
            <li><strong>Uso de la app:</strong> registramos eventos mínimos (por ejemplo,
            que abriste la app o que completaste la evaluación) asociados a tu cuenta, para
            entender qué tan útil es el producto.</li>
        </ul>

        <h2>Con quién se comparte</h2>
        <p>No vendemos tus datos ni los compartimos con anunciantes. Se comparten únicamente
        con:</p>
        <ul>
            <li><strong>Google</strong>, para verificar tu identidad al iniciar sesión.</li>
            <li><strong>Anthropic (Claude API)</strong>, para generar las respuestas del coach
            de IA — solo el mensaje que envías en ese momento, sin tu nombre ni correo.</li>
            <li><strong>Tu empleador</strong>, únicamente si NeoVita te fue asignado como
            beneficio de bienestar por una empresa: en ese caso, la persona administradora de
            esa empresa puede ver tu nombre, correo y los puntajes de tu evaluación (no tus
            conversaciones con el coach ni tus datos de salud crudos).</li>
        </ul>

        <h2>Dónde se almacenan los datos</h2>
        <p>Los datos de cuenta, evaluaciones y planes se guardan en una base de datos
        PostgreSQL operada en Railway. Los datos de salud de tu dispositivo (Health Connect /
        HealthKit) permanecen en el dispositivo salvo por los promedios diarios que subes
        voluntariamente para calcular tu puntaje.</p>

        <h2>Tus opciones</h2>
        <p>Podés dejar de compartir datos de salud desactivando el permiso en Health Connect o
        HealthKit en cualquier momento. Para eliminar tu cuenta y tus datos, escribinos a
        <code>jvillad1@gmail.com</code>.</p>

        <h2>Contacto</h2>
        <p>Preguntas sobre esta política: <code>jvillad1@gmail.com</code>.</p>
    </div></body></html>
""".trimIndent()
