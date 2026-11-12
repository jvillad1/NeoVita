package com.neovita.app.ui.valle

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import com.neovita.app.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

/** Lee los bytes y entrega la familia sólo cuando está completa (ver cargarTitulares en común). */
@OptIn(ExperimentalResourceApi::class)
@Composable
actual fun cargarTitulares(): FontFamily? {
    val familia by produceState<FontFamily?>(null) {
        value = runCatching {
            val bytes = Res.readBytes("font/DMSerifDisplay-Regular.ttf")
            FontFamily(Font("DMSerifDisplay-Regular", bytes, FontWeight.Normal))
        }.getOrNull()
    }
    return familia
}
