package com.neovita.app.auth

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.NSURLComponents
import platform.Foundation.base64EncodedStringWithOptions
import platform.Foundation.create
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecRandomDefault
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.darwin.NSObject
import kotlin.coroutines.resume

// Sign-In nativo sin SDK de terceros: ASWebAuthenticationSession abre la hoja de consentimiento
// de Google y devuelve el callback por esquema (no hace falta registrar CFBundleURLTypes).
// Usamos el flujo de código con PKCE, que es el que Google exige para apps instaladas, y
// canjeamos el código por un id_token — el mismo que ya verifica el servidor.
private const val AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
private const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"

actual class GoogleSignInClient actual constructor() {

    private val http = HttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    actual suspend fun signIn(clients: GoogleClientIds): GoogleSignInResult {
        val clientId = clients.ios?.takeIf { it.isNotBlank() }
            ?: return GoogleSignInResult(
                idToken = null,
                error = "Google Sign-In no está configurado (falta GOOGLE_CLIENT_ID_IOS en el servidor)"
            )

        // El esquema de redirección de Google para iOS es el client id invertido.
        val scheme = reversedClientId(clientId)
        val redirectUri = "$scheme:/oauth2redirect"
        val verifier = randomCodeVerifier()
            ?: return GoogleSignInResult(
                idToken = null,
                error = "No se pudo iniciar el inicio de sesión de forma segura"
            )
        val challenge = base64UrlSha256(verifier)

        val authUrl = "$AUTH_ENDPOINT" +
            "?client_id=$clientId" +
            "&redirect_uri=${redirectUri.encodeURLParameter()}" +
            "&response_type=code" +
            "&scope=${"openid email profile".encodeURLParameter()}" +
            "&code_challenge=$challenge" +
            "&code_challenge_method=S256"

        val callback = when (val outcome = presentAuthSession(authUrl, scheme)) {
            is AuthOutcome.Callback -> outcome.url
            AuthOutcome.Cancelled ->
                return GoogleSignInResult(idToken = null, error = "Inicio de sesión cancelado")
            AuthOutcome.NotStarted ->
                return GoogleSignInResult(idToken = null, error = "No se pudo abrir el inicio de sesión de Google")
        }

        val code = queryValue(callback, "code")
            ?: return GoogleSignInResult(
                idToken = null,
                error = queryValue(callback, "error")?.let { "Google rechazó el inicio de sesión" }
                    ?: "Respuesta inesperada de Google"
            )

        return exchangeCodeForIdToken(code, verifier, clientId, redirectUri)
    }

    actual suspend fun signOut() {
        // El flujo web no deja sesión persistente en la app; no hay nada que limpiar.
    }

    private suspend fun exchangeCodeForIdToken(
        code: String, verifier: String, clientId: String, redirectUri: String
    ): GoogleSignInResult = runCatching {
        val response = http.post(TOKEN_ENDPOINT) {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                "code=${code.encodeURLParameter()}" +
                    "&client_id=$clientId" +
                    "&redirect_uri=${redirectUri.encodeURLParameter()}" +
                    "&grant_type=authorization_code" +
                    "&code_verifier=$verifier"
            )
        }
        val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
        val idToken = body["id_token"]?.jsonPrimitive?.content
        if (idToken.isNullOrBlank()) {
            val googleError = body["error"]?.jsonPrimitive?.content
            GoogleSignInResult(
                idToken = null,
                error = googleError?.let { "Google rechazó el inicio de sesión ($it)" }
                    ?: "Google no devolvió un token de sesión"
            )
        } else {
            GoogleSignInResult(idToken = idToken, error = null)
        }
    }.getOrElse {
        GoogleSignInResult(idToken = null, error = "Error de conexión con Google")
    }

    @OptIn(ExperimentalForeignApi::class)
    private suspend fun presentAuthSession(url: String, scheme: String): AuthOutcome {
        val anchor = resolvePresentationAnchor() ?: return AuthOutcome.NotStarted

        return runCatching {
            suspendCancellableCoroutine<AuthOutcome> { cont ->
                val session = ASWebAuthenticationSession(
                    uRL = NSURL(string = url),
                    callbackURLScheme = scheme
                ) { callbackUrl, _ ->
                    if (cont.isActive) {
                        val outcome = callbackUrl?.absoluteString?.let { AuthOutcome.Callback(it) }
                            ?: AuthOutcome.Cancelled
                        cont.resume(outcome)
                    }
                }
                session.presentationContextProvider = AnchorProvider(anchor)
                session.prefersEphemeralWebBrowserSession = false
                if (!session.start()) {
                    if (cont.isActive) cont.resume(AuthOutcome.NotStarted)
                }
                cont.invokeOnCancellation { session.cancel() }
            }
        }.getOrElse { AuthOutcome.NotStarted }
    }
}

// Resultado del paso de presentación de la sesión de auth: distingue "el usuario canceló"
// de "no se pudo ni siquiera abrir la sesión" (falta de ancla, o fallo al construirla).
private sealed interface AuthOutcome {
    data class Callback(val url: String) : AuthOutcome
    data object Cancelled : AuthOutcome
    data object NotStarted : AuthOutcome
}

// ASWebAuthenticationSession exige una ventana donde presentarse; la resolvemos ANTES de
// arrancar la sesión para no depender de un force-unwrap en el protocolo de presentación.
@OptIn(ExperimentalForeignApi::class)
private fun resolvePresentationAnchor(): UIWindow? {
    UIApplication.sharedApplication.keyWindow?.let { return it }

    val windowScenes = UIApplication.sharedApplication.connectedScenes
        .mapNotNull { it as? UIWindowScene }

    windowScenes.forEach { scene ->
        scene.windows.forEach { window ->
            val uiWindow = window as? UIWindow
            if (uiWindow?.isKeyWindow() == true) return uiWindow
        }
    }

    return windowScenes.firstOrNull()?.windows?.firstOrNull() as? UIWindow
}

private class AnchorProvider(
    private val anchor: UIWindow
) : NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {
    override fun presentationAnchorForWebAuthenticationSession(
        session: ASWebAuthenticationSession
    ): ASPresentationAnchor = anchor
}

// "123-abc.apps.googleusercontent.com" -> "com.googleusercontent.apps.123-abc"
private fun reversedClientId(clientId: String): String =
    "com.googleusercontent.apps." + clientId.removeSuffix(".apps.googleusercontent.com")

@OptIn(ExperimentalForeignApi::class)
private fun randomCodeVerifier(): String? {
    // PKCE exige aleatoriedad criptográfica: kotlin.random.Random NO lo es (documentado por
    // Kotlin como no apto para uso criptográfico). Usamos el CSPRNG del sistema y, si falla,
    // propagamos el error en vez de degradar a un generador débil.
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
    val bytes = ByteArray(64)
    val status = bytes.usePinned { pinned ->
        SecRandomCopyBytes(kSecRandomDefault, bytes.size.convert(), pinned.addressOf(0))
    }
    if (status != errSecSuccess) return null
    return buildString(bytes.size) {
        bytes.forEach { b -> append(chars[(b.toInt() and 0xFF) % chars.length]) }
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun base64UrlSha256(input: String): String {
    val bytes = input.encodeToByteArray()
    val digest = ByteArray(CC_SHA256_DIGEST_LENGTH)
    bytes.usePinned { inPin ->
        digest.usePinned { outPin ->
            CC_SHA256(inPin.addressOf(0), bytes.size.convert(), outPin.addressOf(0).reinterpret())
        }
    }
    val data = digest.usePinned { pin ->
        NSData.create(bytes = pin.addressOf(0), length = digest.size.convert())
    }
    return data.base64EncodedStringWithOptions(0u)
        .replace('+', '-').replace('/', '_').trimEnd('=')
}

private fun queryValue(url: String, name: String): String? =
    NSURLComponents(string = url)?.queryItems
        ?.firstOrNull { (it as? platform.Foundation.NSURLQueryItem)?.name == name }
        ?.let { (it as platform.Foundation.NSURLQueryItem).value }
