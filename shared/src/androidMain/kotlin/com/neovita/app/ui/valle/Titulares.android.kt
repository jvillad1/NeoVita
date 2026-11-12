package com.neovita.app.ui.valle

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.neovita.app.resources.DMSerifDisplay_Regular
import com.neovita.app.resources.Res
import org.jetbrains.compose.resources.Font

@Composable
actual fun cargarTitulares(): FontFamily? = FontFamily(Font(Res.font.DMSerifDisplay_Regular, FontWeight.Normal))
