package com.neovita.server.db

import com.neovita.shared.network.dto.CardDto
import com.neovita.shared.network.dto.ScreenDefinitionDto
import com.neovita.shared.network.dto.SectionDto

/**
 * Initial "dashboard" screen definition, transcribed verbatim from the hard-coded
 * EXP_CARDS / HABIT_CARDS / PRACTICE_CARDS lists in
 * shared/src/commonMain/kotlin/com/neovita/app/screens/dashboard/DashboardScreen.kt.
 * Gradient fallback colors are client-only decoration and do not travel over the wire.
 * Seeded on first boot; afterwards the DB is the source of truth.
 */
fun dashboardScreen(): ScreenDefinitionDto = ScreenDefinitionDto(
    slug = "dashboard",
    version = 1,
    sections = listOf(
        SectionDto(type = "HERO_SCORE"),
        SectionDto(
            type = "CARD_ROW",
            title = "Experiencias Recomendadas",
            cards = listOf(
                CardDto("Yoga al amanecer", "Tulum, México", "https://images.unsplash.com/photo-1506126613408-eca07ce68773?w=300&q=80", "Desde \$25 USD", "4.9"),
                CardDto("Dieta Mediterránea", "Clases en línea", "https://images.unsplash.com/photo-1512621776951-a57141f2eefd?w=300&q=80", "Desde \$25 USD", "4.9"),
                CardDto("Senderismo Grupal", "Valle de Antón, Panamá", "https://images.unsplash.com/photo-1551632811-561732d1e306?w=300&q=80", "Desde \$25 USD", "4.8"),
                CardDto("Meditación Guiada", "En línea", "https://images.unsplash.com/photo-1506905925346-21bda4d32df4?w=300&q=80", "Desde \$25 USD", "4.9"),
                CardDto("Natación al aire libre", "Medellín, Colombia", "https://images.unsplash.com/photo-1571019613454-1cb2f99b2d8b?w=300&q=80", "Desde \$25 USD", "4.7"),
            )
        ),
        SectionDto(
            type = "CARD_ROW",
            title = "Hábitos de Zonas Azules",
            cards = listOf(
                CardDto("Meditación Diaria", "5-20 min · Reduce cortisol", "https://images.unsplash.com/photo-1544367567-0f2fcb009e0b?w=300&q=80", "Gratis", "4.9"),
                CardDto("Dieta Plant-Based", "Zonas Azules · Evidencia científica", "https://images.unsplash.com/photo-1490645935967-10de6ba17061?w=300&q=80", "Plan incluido", "4.8"),
                CardDto("Caminar 10K Pasos", "Movimiento natural · Cada día", "https://images.unsplash.com/photo-1476480862126-209bfaa8edc8?w=300&q=80", "Sin costo", "4.7"),
                CardDto("Sueño 8 Horas", "Ciclos REM · Recuperación celular", "https://images.unsplash.com/photo-1541781774459-bb2af2f05b55?w=300&q=80", "Guía incluida", "4.9"),
                CardDto("Vínculos Sociales", "Tribu · Propósito compartido", "https://images.unsplash.com/photo-1529156069898-49953e39b3ac?w=300&q=80", "Comunidad", "4.8"),
            )
        ),
        SectionDto(
            type = "CARD_ROW",
            title = "Prácticas de Longevidad",
            cards = listOf(
                CardDto("Terapia de Frío", "Wim Hof · Inmunidad y energía", "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=300&q=80", "Técnica libre", "4.8"),
                CardDto("Ayuno 16/8", "Autofagia · Longevidad celular", "https://images.unsplash.com/photo-1498837167922-ddd27525d352?w=300&q=80", "Plan gratis", "4.7"),
                CardDto("Sauna Infrarrojo", "Detox · Cardio pasivo · Piel", "https://images.unsplash.com/photo-1545167622-3a6ac756afa4?w=300&q=80", "Desde \$15 USD", "4.9"),
                CardDto("Respiración 4-7-8", "Sistema nervioso · Sueño profundo", "https://images.unsplash.com/photo-1506905925346-21bda4d32df4?w=300&q=80", "Técnica libre", "4.8"),
                CardDto("Entrenamiento Funcional", "Fuerza · Movilidad · Equilibrio", "https://images.unsplash.com/photo-1517836357463-d25dfeac3438?w=300&q=80", "Desde \$10 USD", "4.7"),
            )
        ),
        SectionDto(type = "CONTENT_FEED", title = "Para ti"),
    )
)

val SEED_SCREENS: List<ScreenDefinitionDto> = listOf(dashboardScreen())
