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
import platform.UIKit.UIApplication
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.random.Random

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
        val challenge = base64UrlSha256(verifier)

        val authUrl = "$AUTH_ENDPOINT" +
            "?client_id=$clientId" +
            "&redirect_uri=${redirectUri.encodeURLParameter()}" +
            "&response_type=code" +
            "&scope=${"openid email profile".encodeURLParameter()}" +
            "&code_challenge=$challenge" +
            "&code_challenge_method=S256"

        val callback = presentAuthSession(authUrl, scheme)
            ?: return GoogleSignInResult(idToken = null, error = "Inicio de sesión cancelado")

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
        val idToken = json.parseToJsonElement(response.bodyAsText())
            .jsonObject["id_token"]?.jsonPrimitive?.content
        if (idToken.isNullOrBlank()) {
            GoogleSignInResult(idToken = null, error = "Google no devolvió un token de sesión")
        } else {
            GoogleSignInResult(idToken = idToken, error = null)
        }
    }.getOrElse {
        GoogleSignInResult(idToken = null, error = "Error de conexión con Google")
    }

    @OptIn(ExperimentalForeignApi::class)
    private suspend fun presentAuthSession(url: String, scheme: String): String? =
        suspendCancellableCoroutine { cont ->
            val session = ASWebAuthenticationSession(
                uRL = NSURL(string = url),
                callbackURLScheme = scheme
            ) { callbackUrl, _ ->
                if (cont.isActive) cont.resume(callbackUrl?.absoluteString)
            }
            session.presentationContextProvider = AnchorProvider()
            session.prefersEphemeralWebBrowserSession = false
            if (!session.start()) {
                if (cont.isActive) cont.resume(null)
            }
            cont.invokeOnCancellation { session.cancel() }
        }
}

// ASWebAuthenticationSession exige una ventana donde presentarse.
private class AnchorProvider : NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {
    override fun presentationAnchorForWebAuthenticationSession(
        session: ASWebAuthenticationSession
    ): ASPresentationAnchor = UIApplication.sharedApplication.keyWindow!!
}

// "123-abc.apps.googleusercontent.com" -> "com.googleusercontent.apps.123-abc"
private fun reversedClientId(clientId: String): String =
    "com.googleusercontent.apps." + clientId.removeSuffix(".apps.googleusercontent.com")

private fun randomCodeVerifier(): String {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
    return (1..64).map { chars[Random.nextInt(chars.length)] }.joinToString("")
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
