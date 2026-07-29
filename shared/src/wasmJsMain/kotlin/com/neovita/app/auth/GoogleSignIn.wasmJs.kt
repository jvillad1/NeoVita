package com.neovita.app.auth

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay

// The web client resolves the OAuth Web Client ID at sign-in time:
// window.NEOVITA_GOOGLE_CLIENT_ID (index.html override) first, else GET /api/config
// (served from the GOOGLE_CLIENT_ID env var). The GIS script itself is loaded by
// webApp/src/wasmJsMain/resources/index.html.

private fun readWindowClientId(): String =
    js("window.NEOVITA_GOOGLE_CLIENT_ID || ''")

private fun fetchServerClientId(onResult: (String) -> Unit): Unit = js(
    """{
        fetch('/api/config')
            .then(function(r) { return r.json(); })
            .then(function(c) { onResult(c.googleClientId || ''); })
            .catch(function() { onResult(''); });
    }"""
)

private fun isGisReady(): Boolean =
    js("typeof google !== 'undefined' && !!(google.accounts && google.accounts.id)")

// Shows a modal overlay with the official GIS button (reliable on every browser) and
// also triggers the One Tap prompt. Whichever the user completes fires onToken once;
// clicking the backdrop dismisses and fires onDismiss.
private fun gisRequestCredential(clientId: String, onToken: (String) -> Unit, onDismiss: () -> Unit): Unit = js(
    """{
        var done = false;
        var finish = function(token) {
            if (done) return;
            done = true;
            google.accounts.id.cancel();
            var overlay = document.getElementById('neovita-gsi-overlay');
            if (overlay) overlay.remove();
            if (token) onToken(token); else onDismiss();
        };

        google.accounts.id.initialize({
            client_id: clientId,
            callback: function(resp) { finish(resp && resp.credential ? resp.credential : null); },
            auto_select: false,
            cancel_on_tap_outside: true,
            use_fedcm_for_prompt: true
        });

        var old = document.getElementById('neovita-gsi-overlay');
        if (old) old.remove();
        var overlay = document.createElement('div');
        overlay.id = 'neovita-gsi-overlay';
        overlay.style.cssText = 'position:fixed;inset:0;z-index:10000;display:flex;align-items:center;' +
            'justify-content:center;background:rgba(0,0,0,0.5)';
        overlay.addEventListener('click', function(e) { if (e.target === overlay) finish(null); });

        var card = document.createElement('div');
        card.style.cssText = 'background:#fff;border-radius:16px;padding:32px 40px;display:flex;' +
            'flex-direction:column;align-items:center;gap:16px;box-shadow:0 8px 32px rgba(0,0,0,0.25)';
        var title = document.createElement('div');
        title.textContent = 'Inicia sesión en NeoVita';
        title.style.cssText = 'font-family:system-ui,sans-serif;font-size:16px;color:#333';
        var buttonHost = document.createElement('div');
        card.appendChild(title);
        card.appendChild(buttonHost);
        overlay.appendChild(card);
        document.body.appendChild(overlay);

        google.accounts.id.renderButton(buttonHost, {
            type: 'standard', theme: 'outline', size: 'large',
            text: 'continue_with', shape: 'pill', locale: 'es'
        });
        google.accounts.id.prompt();
    }"""
)

private fun gisDisableAutoSelect(): Unit = js(
    """{
        if (typeof google !== 'undefined' && google.accounts && google.accounts.id) {
            google.accounts.id.disableAutoSelect();
        }
    }"""
)

actual class GoogleSignInClient actual constructor() {

    actual suspend fun signIn(clients: GoogleClientIds): GoogleSignInResult {
        val clientId = clients.web?.takeIf { it.isNotBlank() } ?: resolveClientId()
        if (clientId.isBlank()) {
            return GoogleSignInResult(
                idToken = null,
                error = "Google Sign-In no está configurado (falta GOOGLE_CLIENT_ID en el servidor)"
            )
        }
        if (!waitForGis()) {
            return GoogleSignInResult(idToken = null, error = "No se pudo cargar Google Sign-In")
        }
        val result = CompletableDeferred<GoogleSignInResult>()
        gisRequestCredential(
            clientId,
            onToken = { result.complete(GoogleSignInResult(idToken = it, error = null)) },
            onDismiss = { result.complete(GoogleSignInResult(idToken = null, error = "Inicio de sesión cancelado")) }
        )
        return result.await()
    }

    actual suspend fun signOut() {
        gisDisableAutoSelect()
    }

    private suspend fun resolveClientId(): String {
        cachedClientId?.let { return it }
        val fromWindow = readWindowClientId()
        val resolved = if (fromWindow.isNotBlank()) {
            fromWindow
        } else {
            val deferred = CompletableDeferred<String>()
            fetchServerClientId { deferred.complete(it) }
            deferred.await()
        }
        if (resolved.isNotBlank()) cachedClientId = resolved
        return resolved
    }

    // The GIS <script> loads async; poll briefly in case the user clicks before it's ready.
    private suspend fun waitForGis(): Boolean {
        repeat(50) {
            if (isGisReady()) return true
            delay(100)
        }
        return false
    }

    private companion object {
        var cachedClientId: String? = null
    }
}
