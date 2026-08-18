package com.neovita.app.auth

import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

actual class GoogleSignInClient actual constructor() {

    actual suspend fun signIn(clients: GoogleClientIds): GoogleSignInResult {
        val serverClientId = clients.web
        val activity = CurrentActivityHolder.activity
            ?: return GoogleSignInResult(idToken = null, error = "No hay una pantalla activa")
        if (serverClientId.isNullOrBlank()) {
            return GoogleSignInResult(
                idToken = null,
                error = "Google Sign-In no está configurado (falta GOOGLE_CLIENT_ID en el servidor)"
            )
        }

        val option = GetGoogleIdOption.Builder()
            .setServerClientId(serverClientId)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        return try {
            val credential = CredentialManager.create(activity)
                .getCredential(activity, request)
                .credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                GoogleSignInResult(idToken = idToken, error = null)
            } else {
                GoogleSignInResult(idToken = null, error = "Credencial inesperada de Google")
            }
        } catch (e: GetCredentialCancellationException) {
            GoogleSignInResult(idToken = null, error = "Inicio de sesión cancelado")
        } catch (e: NoCredentialException) {
            // NoCredential NO significa "no hay cuentas": también lo lanza Google cuando la
            // cuenta existe pero no puede emitir credencial para este cliente OAuth (paquete
            // o SHA-1 sin registrar, app en modo prueba sin este correo, propagación
            // pendiente). Sin el motivo, el mensaje al usuario apunta al sitio equivocado.
            logFallo("NoCredential", e)
            GoogleSignInResult(idToken = null, error = "Google no ofreció ninguna cuenta para esta app")
        } catch (e: GoogleIdTokenParsingException) {
            logFallo("Parsing", e)
            GoogleSignInResult(idToken = null, error = "No se pudo leer la credencial de Google")
        } catch (e: GetCredentialException) {
            logFallo("GetCredential", e)
            GoogleSignInResult(idToken = null, error = "Error al iniciar sesión con Google")
        }
    }

    /** Sin esto el motivo se pierde y sólo queda un texto que puede ser engañoso. */
    private fun logFallo(etapa: String, e: Throwable) {
        println("NEOVITA-SIGNIN-$etapa: ${e::class.simpleName}: ${e.message}")
    }

    actual suspend fun signOut() {
        val activity = CurrentActivityHolder.activity ?: return
        CredentialManager.create(activity).clearCredentialState(ClearCredentialStateRequest())
    }
}
