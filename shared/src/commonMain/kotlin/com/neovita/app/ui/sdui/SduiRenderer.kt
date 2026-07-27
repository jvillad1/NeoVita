package com.neovita.app.ui.sdui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neovita.app.screens.dashboard.ContentItem
import com.neovita.app.ui.components.ScoreRing
import com.neovita.app.ui.theme.*
import com.neovita.shared.domain.model.PillarScores
import com.neovita.shared.network.dto.CardDto
import com.neovita.shared.network.dto.ScreenDefinitionDto
import com.neovita.shared.network.dto.SectionDto
import com.neovita.shared.network.dto.renderableSections

/**
 * Renders a server-provided [ScreenDefinitionDto] for the dashboard. Consumes
 * `renderableSections(definition)` — unknown section types and invalid actions have
 * already been filtered/stripped there, so this renderer never re-validates.
 *
 * Kept fully independent from `DashboardFallback` (no shared composables) per the
 * SDUI-contenido brief: the fallback is the insurance policy and must not be touched.
 */
@Composable
fun SduiRenderer(
    definition: ScreenDefinitionDto,
    scores: PillarScores?,
    feed: List<ContentItem>,
    onNavigateTab: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenWebView: (String, String) -> Unit,
) {
    Box(Modifier.fillMaxSize().background(NeoDarkBg)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            renderableSections(definition).forEach { section ->
                item {
                    SduiSection(
                        section = section,
                        scores = scores,
                        feed = feed,
                        onNavigateTab = onNavigateTab,
                        onOpenUrl = onOpenUrl,
                        onOpenWebView = onOpenWebView,
                    )
                }
            }
        }
    }
}

@Composable
private fun SduiSection(
    section: SectionDto,
    scores: PillarScores?,
    feed: List<ContentItem>,
    onNavigateTab: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenWebView: (String, String) -> Unit,
) {
    val onCardClick: (CardDto) -> (() -> Unit)? = { card ->
        card.action?.let { action ->
            {
                when (action.type) {
                    "NAVIGATE" -> onNavigateTab(action.target)
                    "OPEN_URL" -> onOpenUrl(action.target)
                    "OPEN_WEBVIEW" -> onOpenWebView(card.title, action.target)
                }
            }
        }
    }

    when (section.type) {
        "HERO_SCORE" -> HeroScoreSection(scores, Modifier.padding(horizontal = 22.dp, vertical = 14.dp))

        "CARD_ROW" -> Column {
            section.title?.let { SectionHeader(it) }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(section.cards) { card -> SduiCard(card, onCardClick(card)) }
            }
        }

        "CARD_LIST" -> Column {
            section.title?.let { SectionHeader(it) }
            Column(
                Modifier.padding(horizontal = 22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                section.cards.forEach { card -> SduiCard(card, onCardClick(card)) }
            }
        }

        "QUOTE_BANNER" -> section.text?.let { QuoteBanner(it) }

        "CONTENT_FEED" -> {
            val items = if (section.category != null) {
                feed.filter { it.category.name == section.category }
            } else feed
            Column {
                section.title?.let { SectionHeader(it) }
                Column(
                    Modifier.padding(horizontal = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items.forEach { ContentFeedItemCard(it) }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        color = NeoTextPrimary,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 14.dp)
    )
}

@Composable
private fun QuoteBanner(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NeoCrimson.copy(alpha = 0.08f))
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            color = NeoCrimsonDim,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
    }
}

@Composable
private fun ContentFeedItemCard(item: ContentItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                item.title,
                color = NeoTextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                maxLines = 2
            )
            Spacer(Modifier.height(4.dp))
            Text(
                item.teaser,
                color = NeoTextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 2
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${item.readMinutes} min · ${item.category.label}",
                color = NeoCrimson,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ── HERO_SCORE — independent re-implementation of the current score card, driven by
//    `scores` directly instead of full DashboardState (kept out of DashboardScreen.kt
//    on purpose so the fallback's ScoreCard/PillarStat stay untouched). ─────────────

@Composable
private fun HeroScoreSection(scores: PillarScores?, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        val overall = scores?.overall ?: 85
        val nutricion = scores?.nutrition ?: 88
        val actividad = scores?.exercise ?: 82
        val sueno = scores?.sleep ?: 85
        val bienestar = 84

        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 20.dp)) {

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Longevity Score", color = NeoTextMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text("❤️", fontSize = 18.sp)
            }

            Spacer(Modifier.height(14.dp))

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ScoreRing(score = overall, size = 120.dp, textColor = NeoCrimson)
            }

            Spacer(Modifier.height(10.dp))

            Text(
                "Tu puntuación esta semana",
                color = NeoTextMuted,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth()) {
                HeroPillarStat("Nutrición", nutricion, NeoCrimson, Modifier.weight(1f))
                HeroPillarStat("Actividad", actividad, NeoCrimson, Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                HeroPillarStat("Sueño", sueno, NeoTextSecondary, Modifier.weight(1f))
                HeroPillarStat("Bienestar", bienestar, NeoTextSecondary, Modifier.weight(1f))
            }

            Spacer(Modifier.height(14.dp))

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Ver detalles", color = NeoCrimson, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun HeroPillarStat(label: String, score: Int, dotColor: Color, modifier: Modifier = Modifier) {
    Row(
        modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(dotColor))
        Spacer(Modifier.width(5.dp))
        Text("$label: $score%", color = NeoTextMuted, fontSize = 11.sp)
    }
}
