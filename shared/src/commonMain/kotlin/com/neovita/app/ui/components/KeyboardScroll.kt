package com.neovita.app.ui.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.launch

/**
 * El canvas de Compose Web no traduce las flechas del teclado a scroll como lo hace una página
 * HTML normal — no hay documento, solo un `<canvas>` — así que un contenedor con scroll se queda
 * mudo ante el teclado a menos que pida el foco y mueva su [ScrollableState] a mano. En
 * Android/iOS el toque ya funciona y esto no cambia nada visible, así que se puede aplicar en
 * cualquier pantalla con scroll sin distinguir plataforma.
 *
 * [ScrollState] y [LazyListState] implementan [ScrollableState], así que un solo modifier sirve
 * para columnas normales y para `LazyColumn`.
 */
fun Modifier.keyboardScrollable(state: ScrollableState, step: Float = 120f): Modifier = composed {
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    this
        .focusRequester(focusRequester)
        .focusable(interactionSource = remember { MutableInteractionSource() })
        .onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
            val delta = when (event.key) {
                Key.DirectionDown -> step
                Key.DirectionUp -> -step
                Key.PageDown -> step * 6
                Key.PageUp -> -step * 6
                else -> return@onKeyEvent false
            }
            scope.launch { state.animateScrollBy(delta) }
            true
        }
}
