package com.neovita.app.ui.valle

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * El valle de Medellín de la dirección visual "Valle": montañas, barrios de ladrillo, el Coltejer,
 * el Metrocable, el metro y dos guayacanes. Es la misma escena del lienzo de diseño (kit.py),
 * dibujada en Compose para que corra igual en Android, iOS y web.
 *
 * Toda la geometría se calcula en dp (las constantes son las del lienzo, que estaba en px CSS) y
 * se dibuja escalando por la densidad. Los aleatorios usan semillas fijas, así que la ciudad es
 * siempre la misma: no cambia de forma cada vez que se abre la app.
 *
 * @param florecido los guayacanes pasan de verde a amarillo (p. ej. cuando la persona registró
 *   un hábito hoy).
 * @param animar false deja la escena quieta (movimiento reducido o capturas de pantalla).
 */
@Composable
fun ValleEscena(
    momento: MomentoDelDia,
    modifier: Modifier = Modifier,
    florecido: Boolean = false,
    animar: Boolean = true,
) {
    Spacer(
        modifier
            .semantics { contentDescription = "Las montañas de Medellín, sus barrios de ladrillo y el Metrocable." }
            .valleEscena(momento, florecido, animar)
    )
}

/**
 * La escena como fondo de cualquier composable.
 *
 * @param alturaEscena si se da, el valle ocupa sólo esa franja inferior y todo lo de arriba es
 *   cielo liso del color más alto del degradado. Ahí va el texto: el sol, las nubes y las cabinas
 *   nunca quedan debajo de una letra, sea cual sea el tamaño de fuente del teléfono.
 */
@Composable
fun Modifier.valleEscena(
    momento: MomentoDelDia,
    florecido: Boolean = false,
    animar: Boolean = true,
    alturaEscena: Dp? = null,
    /**
     * Tu cabina, la amarilla: null no la dibuja; false la deja a mitad de cable y true la hace
     * subir hasta la estación (en Vivo, al hacer la caminata del día).
     */
    cabinaLlega: Boolean? = null,
): Modifier {
    val p = paletaValle(momento)
    val cabina by animateFloatAsState(
        targetValue = if (cabinaLlega == true) .78f else .42f,
        animationSpec = tween(2400, easing = FastOutSlowInEasing),
        label = "cabinaPropia",
    )
    val conCabina = cabinaLlega != null
    val flor by animateFloatAsState(
        targetValue = if (florecido) 1f else 0f,
        animationSpec = tween(1600, easing = FastOutSlowInEasing),
        label = "flor",
    )
    val reloj = relojEscena(animar)

    return drawWithCache {
        val d = density
        val w = size.width / d
        val total = size.height / d
        val h = alturaEscena?.value?.coerceAtMost(total) ?: total
        val cielo = total - h
        val g = geometria(w, h, cielo)
        onDrawBehind {
            // Las torres y el suelo se dibujan más allá del borde inferior a propósito; sin recorte
            // asoman sobre lo que haya debajo.
            clipRect {
                scale(d, pivot = Offset.Zero) {
                    drawRect(p.cielo.first(), size = Size(w, cielo + 1f))
                    translate(0f, cielo) { dibujar(g, p, reloj, flor, if (conCabina) cabina else null) }
                }
            }
        }
    }
}

// ── Reloj de animación ─────────────────────────────────────────────────────────

/** Fases 0..1 (o -1..1) de cada movimiento lento de la escena. */
private class RelojEscena(
    val cabinas: State<Float>,
    val nubes: State<Float>,
    val brillo: State<Float>,
    val tren: State<Float>,
    val pajaros: State<Float>,
    val montanas: State<Float>,
    val halo: State<Float>,
    val vaiven: State<Float>,
)

@Composable
private fun relojEscena(animar: Boolean): RelojEscena {
    if (!animar) {
        val quieto = remember { mutableFloatStateOf(0f) }
        val medio = remember { mutableFloatStateOf(.5f) }
        return remember { RelojEscena(medio, quieto, medio, medio, quieto, quieto, medio, quieto) }
    }
    val t = rememberInfiniteTransition(label = "valle")
    fun lineal(ms: Int) = infiniteRepeatable<Float>(tween(ms, easing = LinearEasing), RepeatMode.Restart)
    fun vaiven(ms: Int) = infiniteRepeatable<Float>(tween(ms, easing = FastOutSlowInEasing), RepeatMode.Reverse)
    return RelojEscena(
        cabinas = t.animateFloat(0f, 1f, lineal(90_000), label = "cabinas"),
        nubes = t.animateFloat(-1f, 1f, vaiven(46_000), label = "nubes"),
        brillo = t.animateFloat(0f, 1f, vaiven(3_000), label = "brillo"),
        tren = t.animateFloat(0f, 1f, vaiven(26_000), label = "tren"),
        pajaros = t.animateFloat(0f, 1f, lineal(34_000), label = "pajaros"),
        montanas = t.animateFloat(-1f, 1f, vaiven(38_000), label = "montanas"),
        halo = t.animateFloat(0f, 1f, vaiven(6_000), label = "halo"),
        vaiven = t.animateFloat(-1f, 1f, vaiven(7_000), label = "vaiven"),
    )
}

// ── Geometría (dp) ─────────────────────────────────────────────────────────────

private class Casa(val rect: Rect, val ladrillo: Int, val ventana: Rect?)
private class Torre(val rect: Rect, val ventanas: List<Rect>)
private class Arbol(val x: Float, val y: Float, val s: Float, val copa: List<Pair<Offset, Float>>)
private class Estrella(val c: Offset, val r: Float, val grupo: Int)

private class Geometria(
    val w: Float,
    val h: Float,
    val suelo: Float,
    val lejana: Path,
    val media: Path,
    val casas: List<Casa>,
    val torres: List<Torre>,
    val coltejer: Path,
    val ventanasColtejer: List<Rect>,
    val cableIni: Offset,
    val cableFin: Offset,
    val torresCable: List<Float>,
    val arboles: List<Arbol>,
    val estrellas: List<Estrella>,
    val nubes: List<Triple<Float, Float, Float>>,
)

/** [cieloLibre]: dp de cielo liso encima de la escena; las estrellas también lo ocupan. */
private fun geometria(w: Float, h: Float, cieloLibre: Float = 0f): Geometria {
    val ancho = w >= 600f
    val suelo = h * .86f
    val (lejana, _) = cresta(w, h, h * .54f, h * .30f, h * .03f, 3)
    val (media, puntos) = cresta(w, h, h * .72f, h * .34f, h * .022f, 11)

    val rc = Random(4)
    val casas = buildList {
        val filas = (h * .04f).toInt().coerceAtLeast(1)
        for ((x, cima) in puntos.filterIndexed { i, _ -> i % 2 == 0 }) {
            if (rc.nextFloat() > .8f) continue
            for (j in 0 until filas) {
                val y = cima + 7 + j * 10 + rc.between(-1.5f, 1.5f)
                if (y > suelo - 4) break
                if (rc.nextFloat() > .46f) continue
                val bw = rc.nextInt(8, 14).toFloat()
                val bh = rc.nextInt(7, 11).toFloat()
                val xx = x + rc.between(-3f, 3f)
                val ventana = if (rc.nextFloat() < .55f) Rect(xx + 2, y + 2.5f, xx + 4.6f, y + 5.5f) else null
                add(Casa(Rect(xx, y, xx + bw, y + bh), rc.nextInt(3), ventana))
            }
        }
    }

    val rt = Random(11)
    val centro = w * .5f
    val torres = buildList {
        var x = centro - (w * .2f).toInt()
        while (x < centro + (w * .2f).toInt()) {
            val bw = rt.nextInt(13, 23)
            val dist = abs(x - centro) / (w * .2f)
            val bh = (rt.nextInt(46, 93) * (1.1f - .55f * dist)).toInt()
            val ventanas = buildList {
                var r = 4
                while (r < bh - 4) {
                    var c = 3
                    while (c < bw - 3) {
                        if (rt.nextFloat() < .6f) add(Rect(x + c, suelo - bh + r, x + c + 3, suelo - bh + r + 4))
                        c += 6
                    }
                    r += 9
                }
            }
            add(Torre(Rect(x, suelo - bh, x + bw, suelo + 60), ventanas))
            x += bw + rt.nextInt(2, 10)
        }
    }

    // El Coltejer: la aguja de Medellín.
    val s = if (ancho) 1.1f else .9f
    val cx = centro - 6
    val coltejer = Path().apply {
        moveTo(cx - 9 * s, suelo + 60)
        lineTo(cx - 9 * s, suelo - 104 * s)
        quadraticTo(cx - 7 * s, suelo - 132 * s, cx + 2 * s, suelo - 156 * s)
        quadraticTo(cx + 9 * s, suelo - 130 * s, cx + 9 * s, suelo - 104 * s)
        lineTo(cx + 9 * s, suelo + 60)
        close()
    }
    val ventanasColtejer = buildList {
        var r = 0f
        while (r < 100 * s) {
            for (c in intArrayOf(-5, 0, 5)) {
                if (rt.nextFloat() < .65f) add(Rect(cx + c * s - 1.5f, suelo - 100 * s + r, cx + c * s + 1.5f, suelo - 96 * s + r))
            }
            r += 9
        }
    }

    val arboles = (
        if (ancho) listOf(Triple(90f, h * .97f, 1.2f), Triple(330f, h * .99f, .9f), Triple(w - 320f, h * .98f, .95f), Triple(w - 90f, h * .97f, 1.25f))
        else listOf(Triple(52f, h * .97f, .95f), Triple(w - 44f, h * .985f, 1.05f))
        ).mapIndexed { i, (x, y, sc) ->
        val ra = Random(i + 2)
        Arbol(x, y, sc, List(13) {
            Offset(x + ra.between(-22f, 22f) * sc, y - 58 * sc + ra.between(-16f, 14f) * sc) to ra.between(11f, 17f) * sc
        })
    }

    val re = Random(5)
    val altoEstrellas = cieloLibre + h * .5f
    val estrellas = List((w / 9 * altoEstrellas / (h * .5f)).toInt()) {
        Estrella(Offset(re.nextFloat() * w, re.between(6f - cieloLibre, h * .5f)), floatArrayOf(1f, 1.3f, 1.8f)[re.nextInt(3)], it % 3)
    }

    val nubes = if (ancho) listOf(Triple(w * .52f, h * .14f, .9f), Triple(w * .36f, h * .44f, .7f))
    else listOf(Triple(w * .3f, h * .26f, .9f), Triple(w * .62f, h * .44f, .7f))

    return Geometria(
        w = w, h = h, suelo = suelo, lejana = lejana, media = media, casas = casas, torres = torres,
        coltejer = coltejer, ventanasColtejer = ventanasColtejer,
        cableIni = Offset(-20f, h * .66f), cableFin = Offset(w + 20f, h * .30f),
        torresCable = if (ancho) listOf(.14f, .32f, .5f, .66f) else listOf(.2f, .5f),
        arboles = arboles, estrellas = estrellas, nubes = nubes,
    )
}

/** Silueta de montaña: una parábola (el valle) con tres ondas encima. */
private fun cresta(w: Float, h: Float, base: Float, borde: Float, amp: Float, semilla: Int): Pair<Path, List<Pair<Float, Float>>> {
    val r = Random(semilla)
    val a = r.nextFloat() * 6; val b = r.nextFloat() * 6; val c = r.nextFloat() * 6
    val puntos = buildList {
        var x = -48f
        while (x <= w + 60) {
            val e = (2 * x / w - 1).let { it * it }
            add(x to base - borde * e + amp * (.55f * sin(x / 62 + a) + .3f * sin(x / 28 + b) + .15f * sin(x / 13 + c)))
            x += 8
        }
    }
    val path = Path().apply {
        moveTo(-48f, h + 4)
        puntos.forEach { (x, y) -> lineTo(x, y) }
        lineTo(w + 60, h + 4)
        close()
    }
    return path to puntos
}

private fun Random.between(a: Float, b: Float) = a + nextFloat() * (b - a)

// ── Dibujo ─────────────────────────────────────────────────────────────────────

private val Linea = Color(0xFF1A1230)
private val LuzVentana = Color(0xFFFFD98A)

private fun DrawScope.dibujar(g: Geometria, p: VallePaleta, t: RelojEscena, flor: Float, cabinaPropia: Float?) {
    val w = g.w; val h = g.h

    // Cielo
    drawRect(
        Brush.verticalGradient(*p.paradasCielo.zip(p.cielo).toTypedArray(), startY = 0f, endY = h),
        size = Size(w, h),
    )

    // Estrellas
    if (p.estrellas > 0f) {
        g.estrellas.forEach { e ->
            val fase = (t.brillo.value + e.grupo / 3f) % 1f
            val a = .35f + .65f * (1 - abs(fase * 2 - 1))
            // Detrás del saludo (y < 0) van tenues: un punto brillante junto a una letra parece una tilde.
            val franja = if (e.c.y < 0f) .4f else 1f
            drawCircle(Color.White, e.r, e.c, alpha = p.estrellas * a * franja)
        }
    }

    // Sol (luna de noche) con su halo que respira
    val sol = Offset(w * p.solX, h * p.solY)
    val halo = 120f * (.92f + .22f * t.halo.value)
    drawCircle(
        Brush.radialGradient(listOf(p.sol.copy(alpha = .95f), p.sol.copy(alpha = 0f)), center = sol, radius = halo),
        radius = halo, center = sol, alpha = .62f,
    )
    drawCircle(p.sol, 38f, sol)

    // Nubes
    g.nubes.forEachIndexed { i, (cx, cy, s) ->
        val dx = if (i == 0) -t.nubes.value * 60f + 10f else t.nubes.value * 60f + 10f
        translate(dx, 0f) { nube(cx, cy, s, p.nube) }
    }

    // Pájaros
    if (p.pajaros > 0f) {
        val x = -120f + t.pajaros.value * (w + 260f)
        translate(x, 0f) {
            pajaro(0f, h * .42f, 13f, p.pajaros)
            pajaro(34f, h * .39f, 11f, p.pajaros)
        }
    }

    // Montañas con un paralaje muy lento
    translate(t.montanas.value * 12f, 0f) { drawPath(g.lejana, p.lejana) }
    translate(-t.montanas.value * 12f, 0f) {
        drawPath(g.media, p.media)
        g.casas.forEach { c ->
            drawRect(p.ladrillo[c.ladrillo], c.rect.topLeft, c.rect.size)
            drawRect(p.techo, Offset(c.rect.left - 1, c.rect.top - 2), Size(c.rect.width + 2, 2.4f))
        }
        if (p.luces > 0f) g.casas.forEach { c -> c.ventana?.let { drawRect(LuzVentana, it.topLeft, it.size, alpha = p.luces) } }
    }

    // Metrocable: torres y cable
    val dx = g.cableFin.x - g.cableIni.x; val dy = g.cableFin.y - g.cableIni.y
    fun enCable(f: Float) = Offset(g.cableIni.x + dx * f, g.cableIni.y + dy * f)
    g.torresCable.forEach { f ->
        val o = enCable(f)
        drawLine(Linea, Offset(o.x, o.y - 3), Offset(o.x, g.suelo + 10), 3f, alpha = .55f)
        drawLine(Linea, Offset(o.x - 9, o.y + 3), Offset(o.x + 9, o.y + 3), 3f, alpha = .55f)
    }
    drawLine(Linea, g.cableIni, g.cableFin, 2f, alpha = .65f)

    // Centro: torres y Coltejer
    g.torres.forEach { drawRect(p.cercana, it.rect.topLeft, it.rect.size) }
    drawPath(g.coltejer, p.cercana)
    if (p.luces > 0f) {
        g.torres.forEach { tr -> tr.ventanas.forEach { drawRect(LuzVentana, it.topLeft, it.size, alpha = p.luces) } }
        g.ventanasColtejer.forEach { drawRect(LuzVentana, it.topLeft, it.size, alpha = p.luces) }
    }

    // Metro sobre el río y el suelo del valle
    val trenX = -w * .04f + t.tren.value * w * .55f
    drawRoundRect(Color(0xFFF2EFE9), Offset(trenX, g.suelo - 1), Size(62f, 9f), CornerRadius(3f))
    drawRect(Color(0xFF3B6FA5), Offset(trenX + 4, g.suelo + 1), Size(54f, 2.6f))
    drawRect(p.cercana, Offset(-48f, g.suelo + 8), Size(w + 108, h - g.suelo + 4))

    // Estación arriba
    val est = enCable(.8f)
    drawLine(Linea, Offset(est.x + 22, est.y + 6), Offset(est.x + 22, g.suelo + 20), 5f, alpha = .6f)
    drawRoundRect(p.cercana, Offset(est.x + 4, est.y + 22), Size(44f, 26f), CornerRadius(5f))
    drawPath(Path().apply { moveTo(est.x, est.y + 23); lineTo(est.x + 26, est.y + 6); lineTo(est.x + 52, est.y + 23); close() }, p.cercana)

    // Cabinas subiendo
    listOf(0f, .5f).forEach { desfase ->
        val o = enCable((t.cabinas.value + desfase) % 1f)
        translate(o.x, o.y) { cabina(Color(0xFFFFF3E4)) }
    }
    cabinaPropia?.let { f ->
        val o = enCable(f)
        drawCircle(Color(0xFFFFD27A), 26f, Offset(o.x, o.y + 14f), alpha = .35f)
        translate(o.x, o.y) { cabina(Color(0xFFFFC23C)) }
    }

    // Guayacanes
    val copa = lerp(p.hojaArbol, p.flor, flor)
    val escalaCopa = .8f + .2f * flor
    g.arboles.forEach { a ->
        rotate(t.vaiven.value * 1.2f, pivot = Offset(a.x, a.y)) {
            val tronco = Path().apply {
                moveTo(a.x, a.y)
                cubicTo(a.x - 2 * a.s, a.y - 22 * a.s, a.x + 3 * a.s, a.y - 34 * a.s, a.x, a.y - 52 * a.s)
                moveTo(a.x, a.y - 30 * a.s); lineTo(a.x - 14 * a.s, a.y - 52 * a.s)
                moveTo(a.x, a.y - 34 * a.s); lineTo(a.x + 15 * a.s, a.y - 54 * a.s)
            }
            drawPath(tronco, p.tronco, style = Stroke(4.5f * a.s, cap = StrokeCap.Round))
            scale(escalaCopa, pivot = Offset(a.x, a.y - 50 * a.s)) {
                a.copa.forEach { (c, r) -> drawCircle(copa, r, c) }
            }
        }
    }
}

private fun DrawScope.nube(cx: Float, cy: Float, s: Float, color: Color) {
    val a = .85f
    drawOval(color, Offset(cx - 46 * s, cy - 13 * s), Size(92 * s, 26 * s), alpha = a)
    drawOval(color, Offset(cx - 36 * s, cy - 23 * s), Size(40 * s, 26 * s), alpha = a)
    drawOval(color, Offset(cx - 11 * s, cy - 28 * s), Size(48 * s, 32 * s), alpha = a)
}

private fun DrawScope.pajaro(x: Float, y: Float, ala: Float, alpha: Float) {
    // Dos arcos como la "M0 y q7 -7 13 0 q6 -7 13 0" del lienzo.
    val pico = ala * .54f
    val path = Path().apply {
        moveTo(x, y)
        quadraticTo(x + pico, y - pico, x + ala, y)
        quadraticTo(x + ala + pico, y - pico, x + 2 * ala, y)
    }
    drawPath(path, Linea, alpha = .7f * alpha, style = Stroke(2f, cap = StrokeCap.Round))
}

private fun DrawScope.cabina(color: Color) {
    drawLine(Linea, Offset(0f, -12f), Offset(0f, 4f), 2f)
    drawRoundRect(color, Offset(-12f, 4f), Size(24f, 19f), CornerRadius(5f))
    val vidrio = Color(0xFFBFE3FF)
    drawRoundRect(vidrio, Offset(-8.5f, 8f), Size(7f, 7f), CornerRadius(1.5f))
    drawRoundRect(vidrio, Offset(1.5f, 8f), Size(7f, 7f), CornerRadius(1.5f))
}
