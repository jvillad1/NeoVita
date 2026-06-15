package com.neovita.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.neovita.app.resources.Res
import com.neovita.app.resources.Roboto_Bold
import com.neovita.app.resources.Roboto_Medium
import com.neovita.app.resources.Roboto_Regular
import org.jetbrains.compose.resources.Font

/**
 * App font family.
 *
 * The wasm/web canvas ships no emoji-capable font, so the many emoji used as icons
 * rendered as tofu boxes. skiko/wasm does NOT do per-glyph fallback across the fonts
 * of a FontFamily, so a separate emoji font won't help. Instead, Noto Emoji's glyphs
 * are MERGED into each Roboto weight (regenerate via scripts/merge-emoji-font.py — a
 * single typeface carries both Latin and emoji), rendering on every target, no fallback.
 */
@Composable
fun appFontFamily(): FontFamily = FontFamily(
    Font(Res.font.Roboto_Regular, FontWeight.Normal),
    Font(Res.font.Roboto_Medium, FontWeight.Medium),
    Font(Res.font.Roboto_Bold, FontWeight.Bold),
)

/** Returns this typography with [family] applied to every text style. */
fun Typography.withFontFamily(family: FontFamily) = copy(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)
