package com.neovita.app.ui.sdui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.neovita.shared.network.dto.CardDto
import com.neovita.app.ui.theme.NeoDarkSurface2
import com.neovita.app.ui.theme.NeoTextPrimary
import com.neovita.app.ui.theme.NeoTextSecondary

/**
 * SDUI-driven equivalent of the inline `ExpCardItem` in DashboardScreen.kt — same
 * geometry (130.dp wide, 155.dp image) and typography, but driven entirely by a
 * server-provided [CardDto] instead of a hard-coded list. Per-card gradient fallbacks
 * are replaced by a single standard black scrim (0% → 60%) over the image; [CardDto.badge]
 * renders bottom-left and [CardDto.meta] (rating) renders top-right over the image.
 *
 * Intentionally NOT shared with `DashboardFallback` — the fallback keeps its own
 * `ExpCardItem` untouched as an insurance policy.
 */
@Composable
fun SduiCard(card: CardDto, onClick: (() -> Unit)? = null) {
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier

    Column(Modifier.width(130.dp).then(clickModifier)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(155.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(NeoDarkSurface2)
        ) {
            if (card.imageUrl != null) {
                AsyncImage(
                    model = card.imageUrl,
                    contentDescription = card.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Standard black scrim, 0% -> 60%, replacing the per-card gradient fallback.
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                    )
                )
            )

            // meta (rating) top-right
            card.meta?.let { meta ->
                Row(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⭐", fontSize = 9.sp)
                    Spacer(Modifier.width(2.dp))
                    Text(meta, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // badge bottom-left
            card.badge?.let { badge ->
                Text(
                    badge,
                    modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            card.title,
            color = NeoTextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            maxLines = 2
        )

        card.subtitle?.let {
            Spacer(Modifier.height(3.dp))
            Text(it, color = NeoTextSecondary, fontSize = 10.sp, maxLines = 1)
        }
    }
}
