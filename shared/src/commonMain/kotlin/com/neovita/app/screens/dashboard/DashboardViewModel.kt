package com.neovita.app.screens.dashboard

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.neovita.shared.data.cache.LocalCache
import com.neovita.shared.domain.model.LongevityPlan
import com.neovita.shared.domain.repository.ContentRepository
import com.neovita.shared.domain.repository.PlanRepository
import com.neovita.shared.domain.repository.UserRepository
import com.neovita.shared.network.ApiService
import com.neovita.shared.network.dto.ContentItemDto
import com.neovita.shared.network.dto.ScreenDefinitionDto
import com.neovita.shared.network.dto.UserDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

enum class ContentCategory(val label: String, val emoji: String) {
    NUTRITION("Nutrición", "🥗"),
    EXERCISE("Ejercicio", "🏃"),
    SLEEP("Sueño", "😴"),
    MENTAL_HEALTH("Salud Mental", "🧠"),
    GENERAL("Longevidad", "🌿")
}

enum class ContentType(val label: String) { ARTICLE("Artículo"), TIP("Consejo"), VIDEO("Video") }

data class ContentItem(
    val id: String,
    val title: String,
    val category: ContentCategory,
    val type: ContentType,
    val teaser: String,
    val readMinutes: Int
)

data class DashboardState(
    val user: UserDto? = null,
    val plan: LongevityPlan? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val feed: List<ContentItem> = emptyList(),
    val screenDef: ScreenDefinitionDto? = null,
)

private val ALL_CONTENT = listOf(
    ContentItem("n1", "Los 5 alimentos antiinflamatorios que deberías comer cada semana", ContentCategory.NUTRITION, ContentType.ARTICLE, "La inflamación crónica es uno de los principales aceleradores del envejecimiento. Estos alimentos la combaten de forma natural.", 5),
    ContentItem("n2", "Ayuno intermitente después de los 50: qué dice la ciencia", ContentCategory.NUTRITION, ContentType.ARTICLE, "No todas las ventanas de ayuno son iguales. Descubre cuál se adapta mejor a tu metabolismo a esta edad.", 7),
    ContentItem("n3", "Proteína después de los 40: por qué necesitas más de lo que crees", ContentCategory.NUTRITION, ContentType.TIP, "La sarcopenia comienza antes de lo esperado. Ajustar tu ingesta proteica es la intervención más sencilla y efectiva.", 3),
    ContentItem("e1", "Zona 2: el entrenamiento que los longevos hacen diferente", ContentCategory.EXERCISE, ContentType.ARTICLE, "El cardio de baja intensidad sostenida activa mecanismos mitocondriales que el ejercicio intenso no puede replicar.", 6),
    ContentItem("e2", "Fuerza funcional vs. gimnasio tradicional: qué importa realmente", ContentCategory.EXERCISE, ContentType.ARTICLE, "La capacidad de levantarte del suelo sin apoyo predice tu mortalidad a 10 años. Te explicamos por qué.", 5),
    ContentItem("e3", "10 minutos de movilidad cada mañana: la rutina que cambia todo", ContentCategory.EXERCISE, ContentType.TIP, "No es flexibilidad, es movilidad articular. Esta distinción puede evitarte años de dolor.", 3),
    ContentItem("s1", "La arquitectura del sueño profundo: cómo recuperar tus ondas delta", ContentCategory.SLEEP, ContentType.ARTICLE, "El sueño de ondas lentas es donde ocurre la reparación celular. Aprende qué lo destruye y cómo protegerlo.", 8),
    ContentItem("s2", "Temperatura, luz y ritmo circadiano: los tres reguladores que ignoras", ContentCategory.SLEEP, ContentType.ARTICLE, "Tu cuerpo tiene un reloj maestro. Estas señales ambientales lo sincronizan o lo desajustan.", 6),
    ContentItem("s3", "Por qué cenar tarde arruina más que tu digestión", ContentCategory.SLEEP, ContentType.TIP, "El timing de la última comida afecta directamente tus ciclos de sueño profundo. La ventana óptima te sorprenderá.", 3),
    ContentItem("m1", "Estrés crónico y envejecimiento acelerado: el vínculo que ya no se puede ignorar", ContentCategory.MENTAL_HEALTH, ContentType.ARTICLE, "Los telómeros se acortan con el estrés sostenido. Esto no es metáfora: es biología medible.", 7),
    ContentItem("m2", "Coherencia cardíaca: 5 minutos al día para resetear tu sistema nervioso", ContentCategory.MENTAL_HEALTH, ContentType.TIP, "La variabilidad de la frecuencia cardíaca es el marcador de estrés más confiable. Esta técnica la mejora en semanas.", 4),
    ContentItem("m3", "Propósito de vida y longevidad: lo que los estudios de centenarios revelan", ContentCategory.MENTAL_HEALTH, ContentType.ARTICLE, "El ikigai japonés y el moai no son filosofía: son factores protectores documentados contra la mortalidad prematura.", 9),
    ContentItem("g1", "Los 9 hallazgos del envejecimiento biológico que ya puedes intervenir", ContentCategory.GENERAL, ContentType.ARTICLE, "Desde la disfunción mitocondrial hasta la senescencia celular: un mapa claro de dónde actúa la longevidad moderna.", 10),
    ContentItem("g2", "VO2 máx: el número que predice mejor tu salud futura", ContentCategory.GENERAL, ContentType.ARTICLE, "Más que cualquier análisis de sangre, tu capacidad aeróbica máxima revela tu trayectoria de salud.", 6),
    ContentItem("g3", "Las Zonas Azules: 5 hábitos universales de los pueblos más longevos", ContentCategory.GENERAL, ContentType.TIP, "Okinawa, Cerdeña, Nicoya. Culturas distintas, patrones sorprendentemente idénticos.", 4)
)

class DashboardViewModel(
    private val userRepo: UserRepository,
    private val planRepo: PlanRepository,
    private val contentRepo: ContentRepository,
    private val apiService: ApiService,
    private val localCache: LocalCache?,
) {
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val _state = MutableStateFlow(DashboardState())
    val state = _state.asStateFlow()

    init { load(); loadScreen() }

    private fun load() {
        scope.launch {
            _state.update { it.copy(isLoading = true) }
            val user = userRepo.getMe().getOrNull()
            val plan = planRepo.getCurrent().getOrNull()
            // Content is server-managed (CRUD on /api/content). Fall back to the bundled
            // list only when offline / before the server has seeded.
            val content = contentRepo.getContent().getOrNull()
                ?.map { it.toItem() }
                ?.takeIf { it.isNotEmpty() }
                ?: ALL_CONTENT
            _state.update { it.copy(user = user, plan = plan, isLoading = false, feed = buildFeed(plan, content)) }
        }
    }

    // Load order: cache -> fetch (conditional on cached version) -> fallback null.
    //  - New DTO from the server: use it, and re-cache it (re-serialized) under its version.
    //  - null (304 Not Modified): the cache is still fresh — decode and use the cached json.
    //  - Failure, or no cache and no successful fetch: screenDef stays null (renderer falls
    //    back to DashboardFallback).
    private fun loadScreen() {
        scope.launch {
            // Any cache/storage failure (corrupt DB, driver issue, etc.) degrades to
            // network-or-fallback instead of crashing this coroutine.
            val cached = runCatching { localCache?.getScreen("dashboard") }.getOrNull()
            val result = apiService.getScreen("dashboard", cached?.version)
            val def = result.fold(
                onSuccess = { dto ->
                    when {
                        dto != null -> {
                            val json = Json.encodeToString(ScreenDefinitionDto.serializer(), dto)
                            runCatching { localCache?.cacheScreen(dto.slug, dto.version, json) }
                            dto
                        }
                        cached != null -> decodeCachedScreen(cached.json)
                        else -> null
                    }
                },
                onFailure = { cached?.let { decodeCachedScreen(it.json) } }
            )
            _state.update { it.copy(screenDef = def) }
        }
    }

    private fun decodeCachedScreen(json: String): ScreenDefinitionDto? =
        runCatching { Json.decodeFromString(ScreenDefinitionDto.serializer(), json) }.getOrNull()

    // Surfaces content for the user's weakest pillars first
    private fun buildFeed(plan: LongevityPlan?, content: List<ContentItem>): List<ContentItem> {
        if (plan == null) return content.shuffled().take(6)

        val pillarOrder = listOf(
            ContentCategory.EXERCISE to plan.scores.exercise,
            ContentCategory.SLEEP to plan.scores.sleep,
            ContentCategory.NUTRITION to plan.scores.nutrition,
            ContentCategory.MENTAL_HEALTH to 50,
            ContentCategory.GENERAL to 70
        ).sortedBy { it.second }.map { it.first }

        return pillarOrder.flatMap { category ->
            content.filter { it.category == category }
        }
    }
}

private fun ContentItemDto.toItem() = ContentItem(
    id = id,
    title = title,
    category = runCatching { ContentCategory.valueOf(category) }.getOrDefault(ContentCategory.GENERAL),
    type = runCatching { ContentType.valueOf(type) }.getOrDefault(ContentType.ARTICLE),
    teaser = teaser,
    readMinutes = readMinutes,
)
