package com.example.mediminder.ui.components

import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.mediminder.ui.theme.Hue
import com.example.mediminder.ui.theme.LocalDarkTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/*
 * Small animated scenes for empty screens and the first-run pages.
 *
 * Each one draws itself when it appears: the coloured backdrop pops in, the outlines trace
 * themselves like a pen drawing, the colours fill in and the line fades — then the scene keeps
 * gently moving in a loop (a capsule hops, steam rises, a bell swings). With "Remove
 * animations" on, the finished picture shows at once and holds still.
 *
 * Everything is drawn on a 100 × 100 grid and scaled to the size asked for.
 */

enum class Art(internal val periodMillis: Int, internal val still: Float) {
    /** A pill bottle with a capsule and a tablet hopping beside it. */
    PILLS(2400, 0f),
    /** A calendar: a day gets circled and a heart pops in. */
    CALENDAR(3200, 0.72f),
    /** A clipboard whose ticks write themselves one by one. */
    CHECKLIST(3600, 0.84f),
    /** A shopping bag that gets a tick and bounces. */
    BAG(2800, 0.62f),
    /** A folder with a paper sliding in and out. */
    FOLDER(3200, 0.5f),
    /** A bowl of rice with steam rising. */
    MEAL(3000, 0.3f),
    /** A bell swinging with sound waves. */
    BELL(2400, 0.12f),
    /** An alarm clock whose hands go round, with a little ring at the top of each turn. */
    CLOCK(4000, 0.3f),
    /** A cup of tea with steam — "nothing to take, have a rest". */
    TEA(3000, 0.3f),
    /** A shield that gets a tick, then a calm pulse — "you're protected". */
    SHIELD(3000, 0.62f),
    /** An ID card with a heartbeat line running across it. */
    HEARTBEAT(2600, 0.55f),
    /** A weekly pill organiser whose lids open in a wave. */
    PILLBOX(3600, 0.45f),
    /** A heart that beats, with dots circling it. */
    HEART(3600, 0f),
    /** A phone that buzzes as a reminder slides in. */
    PHONE(3000, 0.45f),
    /** A magnifying glass gliding over a medicine box. */
    SEARCH(3200, 0.5f),
    /** A medicine box that gets a check stamp. */
    BOXCHECK(3000, 0.6f),
    /** The sun sets and the moon rises. */
    SUNMOON(4800, 0.25f),
    /** Tablets drop into three slots, up to a limit. */
    COUNTER(3600, 0.8f),
    /** A stethoscope listening to a heartbeat. */
    STETHOSCOPE(2400, 0.4f),
    /** A gold medal that swings, with a shine. */
    MEDAL(2800, 0.3f),
    /** Two gears turning together. */
    GEARS(4000, 0f),
    /** A gift box whose lid pops open. */
    GIFT(2600, 0.35f),
    /** Bars that grow, with a trend line. */
    CHART(3200, 0.8f),
}

private const val DRAW_IN_MILLIS = 1900

@Composable
fun AnimatedArt(art: Art, hue: Hue, modifier: Modifier = Modifier, size: Dp = 176.dp) {
    val reduce = rememberReduceMotion()
    val reveal = remember(art) { Animatable(if (reduce) 1f else 0f) }
    var t by remember(art) { mutableFloatStateOf(art.still) }
    LaunchedEffect(art, reduce) {
        if (reduce) {
            reveal.snapTo(1f)
            return@LaunchedEffect
        }
        // 1. draw itself (holding the "still" pose) …
        reveal.animateTo(1f, tween(DRAW_IN_MILLIS, easing = LinearEasing))
        // 2. … then keep moving, starting from that same pose so nothing jumps
        val start = withFrameMillis { it }
        while (true) {
            withInfiniteAnimationFrameMillis { now ->
                t = (art.still + (now - start) / art.periodMillis.toFloat()) % 1f
            }
        }
    }
    ArtFrame(art, hue, t = { t }, reveal = { reveal.value }, modifier = modifier.size(size))
}

private class ArtColors(
    val main: Color,
    val soft: Color,
    val paper: Color,
    val ink: Color,
    val accent: Color,
    val shadow: Color,
    /** marks drawn on top of [main]: white by day, deep by night (main is light then) */
    val onMain: Color,
)

/**
 * One frame of [art]: [t] is the time through its loop (0..1), [reveal] how far it has drawn
 * itself (0 = nothing yet, 1 = finished). Both are read while drawing, so a moving scene only
 * redraws — it never recomposes.
 */
@Composable
internal fun ArtFrame(art: Art, hue: Hue, t: () -> Float, reveal: () -> Float = { 1f }, modifier: Modifier) {
    val dark = LocalDarkTheme.current
    val c = ArtColors(
        main = hue.fg(dark),
        soft = hue.bg(dark),
        paper = if (dark) Color(0xFFE8E9EF) else Color.White,
        ink = Color(0xFF1C1B1F),
        accent = if (dark) Color(0xFFFF9E85) else Color(0xFFE2603F),
        shadow = Color.Black.copy(alpha = if (dark) 0.35f else 0.10f),
        onMain = if (dark) hue.bg(true) else Color.White,
    )
    Box(modifier) {
        // The backdrop pops in first
        Canvas(
            Modifier.matchParentSize().graphicsLayer {
                val r = reveal()
                val b = easeOutBack(clamp01(r / 0.3f))
                scaleX = 0.6f + 0.4f * b
                scaleY = 0.6f + 0.4f * b
                alpha = clamp01(r / 0.12f)
            },
        ) {
            val g = grid()
            val time = t()
            val sparkles = clamp01((reveal() - 0.78f) / 0.22f)
            drawCircle(c.soft, radius = 44f * g.u, center = g.o(50f, 52f))
            sparkle(g.o(13f, 22f), 5.5f * g.u * sparkles, c.main.copy(alpha = twinkle(time, 0f)))
            sparkle(g.o(89f, 36f), 3.8f * g.u * sparkles, c.main.copy(alpha = twinkle(time, 0.45f)))
            drawCircle(c.main.copy(alpha = 0.22f * sparkles), radius = 2.4f * g.u, center = g.o(86f, 82f))
            drawCircle(c.main.copy(alpha = 0.16f * sparkles), radius = 1.8f * g.u, center = g.o(10f, 70f))
        }
        // The picture itself: its colours fill in once the outline is mostly drawn
        Canvas(
            Modifier.matchParentSize().graphicsLayer {
                val f = smooth(clamp01((reveal() - 0.5f) / 0.32f))
                alpha = f
                scaleX = 0.97f + 0.03f * f
                scaleY = 0.97f + 0.03f * f
            },
        ) {
            scene(art, grid(), t(), c, tracer = null)
        }
        // The pen line that draws it, fading once the colours are in
        Canvas(Modifier.matchParentSize()) {
            val r = reveal()
            if (r >= 1f) return@Canvas
            val ink = 1f - clamp01((r - 0.7f) / 0.28f)
            val progress = clamp01((r - 0.06f) / 0.6f)
            scene(art, grid(), t(), c, Tracer(progress, c.main.copy(alpha = ink), 2.2f * grid().u))
        }
    }
}

private fun DrawScope.grid(): Grid {
    val u = size.minDimension / 100f
    return Grid(u, (size.width - 100f * u) / 2f, (size.height - 100f * u) / 2f)
}

private fun DrawScope.scene(art: Art, g: Grid, t: Float, c: ArtColors, tracer: Tracer?) = when (art) {
    Art.PILLS -> pills(g, t, c, tracer)
    Art.CALENDAR -> calendar(g, t, c, tracer)
    Art.CHECKLIST -> checklist(g, t, c, tracer)
    Art.BAG -> bag(g, t, c, tracer)
    Art.FOLDER -> folder(g, t, c, tracer)
    Art.MEAL -> meal(g, t, c, tracer)
    Art.BELL -> bell(g, t, c, tracer)
    Art.CLOCK -> clock(g, t, c, tracer)
    Art.TEA -> tea(g, t, c, tracer)
    Art.SHIELD -> shield(g, t, c, tracer)
    Art.HEARTBEAT -> heartbeat(g, t, c, tracer)
    Art.PILLBOX -> pillbox(g, t, c, tracer)
    Art.HEART -> heartScene(g, t, c, tracer)
    Art.PHONE -> phone(g, t, c, tracer)
    Art.SEARCH -> search(g, t, c, tracer)
    Art.BOXCHECK -> boxCheck(g, t, c, tracer)
    Art.SUNMOON -> sunMoon(g, t, c, tracer)
    Art.COUNTER -> counter(g, t, c, tracer)
    Art.STETHOSCOPE -> stethoscope(g, t, c, tracer)
    Art.MEDAL -> medal(g, t, c, tracer)
    Art.GEARS -> gears(g, t, c, tracer)
    Art.GIFT -> gift(g, t, c, tracer)
    Art.CHART -> chart(g, t, c, tracer)
}

/** Grid units → pixels, and the shapes the scenes are made of. */
private class Grid(val u: Float, private val ox: Float, private val oy: Float) {
    fun x(v: Float) = ox + v * u
    fun y(v: Float) = oy + v * u
    fun o(x: Float, y: Float) = Offset(x(x), y(y))
    fun s(w: Float, h: Float) = Size(w * u, h * u)
    fun r(v: Float) = CornerRadius(v * u)

    fun roundRect(x: Float, y: Float, w: Float, h: Float, r: Float) =
        Path().apply { addRoundRect(RoundRect(x(x), y(y), x(x + w), y(y + h), CornerRadius(r * u))) }
    fun rect(x: Float, y: Float, w: Float, h: Float) = Path().apply { addRect(Rect(x(x), y(y), x(x + w), y(y + h))) }
    fun circle(cx: Float, cy: Float, r: Float) = Path().apply { addOval(Rect(o(cx, cy), r * u)) }
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) = Path().apply { moveTo(x(x1), y(y1)); lineTo(x(x2), y(y2)) }
    fun arc(l: Float, t: Float, r: Float, b: Float, start: Float, sweep: Float, close: Boolean = false) = Path().apply {
        arcTo(Rect(x(l), y(t), x(r), y(b)), start, sweep, forceMoveTo = true)
        if (close) close()
    }
}

/**
 * While a scene draws itself, its main shapes are traced as a line instead of filled — one
 * after another, each starting a little after the last.
 */
private class Tracer(private val progress: Float, private val color: Color, private val width: Float) {
    private var index = 0
    fun trace(scope: DrawScope, path: Path) {
        val p = clamp01((progress - index * 0.09f) / 0.62f)
        index++
        scope.drawPartial(path, smooth(p), color, width)
    }
}

/** A main shape: filled normally, traced while drawing itself. */
private fun DrawScope.part(tracer: Tracer?, path: Path, fill: Color) {
    if (tracer == null) drawPath(path, fill) else tracer.trace(this, path)
}

private const val TAU = (2.0 * PI).toFloat()
private fun clamp01(v: Float) = v.coerceIn(0f, 1f)
private fun easeOut(v: Float) = 1f - (1f - v) * (1f - v) * (1f - v)
private fun smooth(v: Float) = v * v * (3f - 2f * v)
private fun easeOutBack(v: Float): Float {
    val c1 = 1.70158f
    val x = v - 1f
    return 1f + (c1 + 1f) * x * x * x + c1 * x * x
}
/** 0 outside [a, b], a smooth hump inside. */
private fun bump(t: Float, a: Float, b: Float) = if (t <= a || t >= b) 0f else sin(PI.toFloat() * (t - a) / (b - a))

/** Draws the first [fraction] of [path] as a line — for things that write themselves. */
private fun DrawScope.drawPartial(path: Path, fraction: Float, color: Color, width: Float) {
    if (fraction <= 0f || color.alpha <= 0f) return
    val measure = PathMeasure().apply { setPath(path, false) }
    val part = Path()
    measure.getSegment(0f, measure.length * clamp01(fraction), part, true)
    drawPath(part, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.heart(center: Offset, s: Float, color: Color) {
    val p = Path().apply {
        moveTo(center.x, center.y + s * 0.9f)
        cubicTo(center.x - s * 1.5f, center.y - s * 0.1f, center.x - s * 0.9f, center.y - s * 1.3f, center.x, center.y - s * 0.45f)
        cubicTo(center.x + s * 0.9f, center.y - s * 1.3f, center.x + s * 1.5f, center.y - s * 0.1f, center.x, center.y + s * 0.9f)
        close()
    }
    drawPath(p, color)
}

// ── Scenes ──────────────────────────────────────────────────────────────────

private fun DrawScope.pills(g: Grid, t: Float, c: ArtColors, tr: Tracer?) {
    val u = g.u
    // The bottle sways a touch
    if (tr == null) drawOval(c.shadow, topLeft = g.o(27f, 82f), size = g.s(32f, 5f))
    rotate(sin(TAU * t) * 2.5f, pivot = g.o(43f, 84f)) {
        part(tr, g.roundRect(29f, 40f, 28f, 44f, 7f), c.paper)
        part(tr, g.rect(29f, 54f, 28f, 16f), c.main)
        part(tr, g.roundRect(31f, 30f, 24f, 11f, 3f), c.main)
        if (tr == null) {
            drawRect(c.onMain, topLeft = g.o(41.5f, 57.5f), size = g.s(3f, 9f))
            drawRect(c.onMain, topLeft = g.o(38.5f, 60.5f), size = g.s(9f, 3f))
            for (i in 0..3) drawRect(c.onMain.copy(alpha = 0.35f), topLeft = g.o(34.5f + i * 5.2f, 32f), size = g.s(1.6f, 7f))
        }
    }

    // A capsule hops and flips once per loop
    val h = abs(sin(PI.toFloat() * t))
    val cx = 72f
    val cy = 84f - 5f - h * 24f
    if (tr == null) {
        drawOval(c.shadow.copy(alpha = c.shadow.alpha * (1f - 0.6f * h)), topLeft = g.o(cx - (9f - 4f * h), 82.5f), size = g.s(18f - 8f * h, 3.5f))
    }
    val squash = if (h < 0.12f) 0.18f * (1f - h / 0.12f) else 0f
    rotate(360f * smooth(t), pivot = g.o(cx, cy)) {
        scale(1f + squash, 1f - squash, pivot = g.o(cx, cy + 5f)) {
            val capsule = g.roundRect(cx - 11f, cy - 5f, 22f, 10f, 5f)
            if (tr != null) tr.trace(this, capsule) else {
                drawPath(capsule, c.paper)
                clipRect(left = g.x(cx - 11f), top = g.y(cy - 6f), right = g.x(cx), bottom = g.y(cy + 6f)) { drawPath(capsule, c.main) }
                drawPath(capsule, c.main.copy(alpha = 0.45f), style = Stroke(1f * u))
            }
        }
    }

    // A plain white tablet (no outline or slash, so it never reads as a "no" sign)
    val h2 = abs(sin(PI.toFloat() * ((t + 0.5f) % 1f)))
    val tx = 87f
    val ty = 84f - 5f - h2 * 13f
    val tablet = g.circle(tx, ty, 5.2f)
    if (tr != null) tr.trace(this, tablet) else {
        drawOval(c.shadow.copy(alpha = c.shadow.alpha * (1f - 0.6f * h2)), topLeft = g.o(tx - (5f - 2f * h2), 82.5f), size = g.s(10f - 4f * h2, 3f))
        drawPath(tablet, c.paper)
        drawCircle(c.main.copy(alpha = 0.18f), radius = 5.2f * u, center = g.o(tx + 0.8f, ty + 0.9f), style = Stroke(0.9f * u))
        drawLine(c.main.copy(alpha = 0.3f), g.o(tx - 2.4f, ty), g.o(tx + 2.4f, ty), strokeWidth = 0.9f * u, cap = StrokeCap.Round)
    }
}

private fun DrawScope.calendar(g: Grid, t: Float, c: ArtColors, tr: Tracer?) {
    val u = g.u
    val fade = 1f - clamp01((t - 0.86f) / 0.14f)
    translate(top = sin(TAU * t) * 1.5f * u) {
        if (tr == null) drawRoundRect(c.shadow, topLeft = g.o(25f, 33f), size = g.s(54f, 52f), cornerRadius = g.r(6f))
        val card = g.roundRect(23f, 30f, 54f, 52f, 6f)
        part(tr, card, c.paper)
        if (tr != null) tr.trace(this, g.line(23f, 44f, 77f, 44f))
        else clipRect(left = g.x(23f), top = g.y(30f), right = g.x(77f), bottom = g.y(44f)) { drawPath(card, c.main) }
        part(tr, g.roundRect(34f, 24f, 4f, 11f, 2f), c.ink.copy(alpha = 0.55f))
        part(tr, g.roundRect(62f, 24f, 4f, 11f, 2f), c.ink.copy(alpha = 0.55f))
        if (tr != null) return@translate

        val dayX = 56f
        val dayY = 63f
        for (row in 0..2) for (col in 0..3) {
            val x = 32f + col * 12f
            val y = 53f + row * 10f
            if (x == dayX && y == dayY) continue
            drawCircle(c.ink.copy(alpha = 0.16f), radius = 2.3f * u, center = g.o(x, y))
        }
        // The visit day: a circle draws round it, then a heart pops in
        val ring = easeOut(clamp01(t / 0.42f))
        drawCircle(c.ink.copy(alpha = 0.16f * (1f - ring)), radius = 2.3f * u, center = g.o(dayX, dayY))
        drawArc(
            c.main.copy(alpha = fade), startAngle = -90f, sweepAngle = 360f * ring, useCenter = false,
            topLeft = g.o(dayX - 6.5f, dayY - 6.5f), size = g.s(13f, 13f), style = Stroke(2.4f * u, cap = StrokeCap.Round),
        )
        val pop = easeOutBack(clamp01((t - 0.42f) / 0.22f))
        if (pop > 0f) heart(g.o(dayX, dayY + 0.5f), 4.2f * u * pop, c.accent.copy(alpha = fade))
    }
}

private fun DrawScope.checklist(g: Grid, t: Float, c: ArtColors, tr: Tracer?) {
    val u = g.u
    val fade = 1f - clamp01((t - 0.9f) / 0.1f)
    if (tr == null) drawRoundRect(c.shadow, topLeft = g.o(29f, 27f), size = g.s(46f, 60f), cornerRadius = g.r(6f))
    part(tr, g.roundRect(27f, 24f, 46f, 60f, 6f), c.main)
    part(tr, g.roundRect(31f, 31f, 38f, 49f, 3f), c.paper)
    part(tr, g.roundRect(41f, 20f, 18f, 8f, 3f), c.ink.copy(alpha = 0.6f))
    for (i in 0..2) {
        val y = 43f + i * 12f
        if (tr != null) {
            tr.trace(this, g.line(47f, y - 1.6f, 63f, y - 1.6f))
            continue
        }
        val p = clamp01((t - 0.06f - i * 0.22f) / 0.2f) * fade
        drawCircle(c.main.copy(alpha = 0.2f + 0.8f * easeOut(p)), radius = 4f * u, center = g.o(39f, y))
        drawLine(c.ink.copy(alpha = 0.16f), g.o(47f, y - 1.6f), g.o(63f, y - 1.6f), strokeWidth = 2.2f * u, cap = StrokeCap.Round)
        drawLine(c.ink.copy(alpha = 0.10f), g.o(47f, y + 2.2f), g.o(57f, y + 2.2f), strokeWidth = 2.2f * u, cap = StrokeCap.Round)
        val tick = Path().apply {
            moveTo(g.x(37f), g.y(y + 0.1f))
            lineTo(g.x(38.8f), g.y(y + 1.9f))
            lineTo(g.x(41.6f), g.y(y - 1.8f))
        }
        drawPartial(tick, easeOut(p), c.onMain, 1.6f * u)
    }
}

private fun DrawScope.bag(g: Grid, t: Float, c: ArtColors, tr: Tracer?) {
    val u = g.u
    val fade = 1f - clamp01((t - 0.88f) / 0.12f)
    val p = easeOut(clamp01((t - 0.08f) / 0.32f)) * fade
    val bounce = 1f + 0.08f * bump(t, 0.38f, 0.58f)
    if (tr == null) drawOval(c.shadow, topLeft = g.o(29f, 82f), size = g.s(42f, 5f))
    scale(bounce, bounce, pivot = g.o(50f, 84f)) {
        val handle = g.arc(39f, 27f, 61f, 53f, 180f, 180f)
        if (tr != null) tr.trace(this, handle)
        else drawPath(handle, c.main, style = Stroke(3.4f * u, cap = StrokeCap.Round))
        part(tr, g.roundRect(28f, 40f, 44f, 44f, 7f), c.main)
        if (tr != null) return@scale
        drawRect(c.onMain.copy(alpha = 0.22f), topLeft = g.o(28f, 46f), size = g.s(44f, 3f))
        val tick = Path().apply {
            moveTo(g.x(40f), g.y(64f))
            lineTo(g.x(47f), g.y(71f))
            lineTo(g.x(61f), g.y(56f))
        }
        drawPartial(tick, p, c.onMain, 4.2f * u)
    }
}

private fun DrawScope.folder(g: Grid, t: Float, c: ArtColors, tr: Tracer?) {
    val u = g.u
    val out = 0.5f - 0.5f * cos(TAU * t)
    val paperY = 47f - 21f * smooth(out)
    if (tr == null) drawOval(c.shadow, topLeft = g.o(26f, 81f), size = g.s(50f, 5f))
    part(tr, g.roundRect(24f, 36f, 52f, 47f, 5f), c.main.copy(alpha = 0.6f))
    part(tr, g.roundRect(24f, 31f, 20f, 10f, 3f), c.main.copy(alpha = 0.6f))
    // The paper, with a little picture and lines on it
    rotate(-4f * out, pivot = g.o(50f, paperY + 17f)) {
        part(tr, g.roundRect(31f, paperY, 38f, 34f, 2.5f), c.paper)
        if (tr == null) {
            drawRoundRect(c.main.copy(alpha = 0.35f), topLeft = g.o(35f, paperY + 5f), size = g.s(9f, 9f), cornerRadius = g.r(1.5f))
            drawLine(c.ink.copy(alpha = 0.16f), g.o(48f, paperY + 7f), g.o(64f, paperY + 7f), strokeWidth = 2f * u, cap = StrokeCap.Round)
            drawLine(c.ink.copy(alpha = 0.12f), g.o(48f, paperY + 12f), g.o(60f, paperY + 12f), strokeWidth = 2f * u, cap = StrokeCap.Round)
            drawLine(c.ink.copy(alpha = 0.12f), g.o(35f, paperY + 19f), g.o(64f, paperY + 19f), strokeWidth = 2f * u, cap = StrokeCap.Round)
        }
    }
    part(tr, g.roundRect(24f, 51f, 52f, 32f, 5f), c.main)
    if (tr == null) drawRect(c.onMain.copy(alpha = 0.18f), topLeft = g.o(24f, 54f), size = g.s(52f, 2f))
}

private fun DrawScope.meal(g: Grid, t: Float, c: ArtColors, tr: Tracer?) {
    val u = g.u
    if (tr == null) drawOval(c.shadow, topLeft = g.o(30f, 81f), size = g.s(40f, 5f))
    part(tr, g.arc(26f, 32f, 74f, 80f, 0f, 180f, close = true), c.main)     // the bowl
    part(tr, g.arc(29f, 42f, 71f, 70f, 180f, 180f, close = true), c.paper)  // a mound of rice
    part(tr, g.roundRect(24f, 54f, 52f, 5f, 2.5f), c.main)                  // the rim
    part(tr, g.roundRect(42f, 77f, 16f, 6f, 2f), c.main)                    // the foot
    if (tr != null) return
    listOf(38f to 50f, 46f to 47f, 55f to 49f, 62f to 52f, 50f to 52f).forEach { (x, y) ->
        drawOval(c.ink.copy(alpha = 0.08f), topLeft = g.o(x, y), size = g.s(3.2f, 1.6f))
    }
    drawArc(
        c.onMain.copy(alpha = 0.25f), startAngle = 25f, sweepAngle = 50f, useCenter = false,
        topLeft = g.o(30f, 36f), size = g.s(40f, 40f), style = Stroke(2f * u, cap = StrokeCap.Round),
    )
    // Steam: three wisps rising and fading in turn
    for (i in 0..2) {
        val ph = (t + i / 3f) % 1f
        val x = 41f + i * 9f
        val rise = ph * 10f
        val alpha = sin(PI.toFloat() * ph) * 0.85f
        val wisp = Path().apply {
            moveTo(g.x(x), g.y(40f - rise))
            quadraticTo(g.x(x - 3.2f), g.y(36f - rise), g.x(x), g.y(32f - rise))
            quadraticTo(g.x(x + 3.2f), g.y(28f - rise), g.x(x), g.y(24f - rise))
        }
        drawPath(wisp, c.main.copy(alpha = alpha), style = Stroke(2.4f * u, cap = StrokeCap.Round))
    }
}

private fun DrawScope.bell(g: Grid, t: Float, c: ArtColors, tr: Tracer?) {
    val u = g.u
    if (tr == null) {
        // Sound waves on both sides
        for (k in 0..1) {
            val p = (t * 2f + k * 0.5f) % 1f
            val r = 25f + 10f * p
            val a = (1f - p) * 0.75f
            drawArc(c.main.copy(alpha = a), 145f, 70f, false, topLeft = g.o(50f - r, 47f - r), size = g.s(2 * r, 2 * r), style = Stroke(2.4f * u, cap = StrokeCap.Round))
            drawArc(c.main.copy(alpha = a), -35f, 70f, false, topLeft = g.o(50f - r, 47f - r), size = g.s(2 * r, 2 * r), style = Stroke(2.4f * u, cap = StrokeCap.Round))
        }
        drawOval(c.shadow, topLeft = g.o(34f, 82f), size = g.s(32f, 4f))
    }
    val angle = 13f * sin(TAU * 2f * t) * (0.65f + 0.35f * cos(TAU * t))
    rotate(angle, pivot = g.o(50f, 22f)) {
        val body = Path().apply {
            moveTo(g.x(50f), g.y(26f))
            cubicTo(g.x(60f), g.y(26f), g.x(64f), g.y(34f), g.x(64f), g.y(46f))
            cubicTo(g.x(64f), g.y(56f), g.x(66f), g.y(60f), g.x(70f), g.y(64f))
            lineTo(g.x(30f), g.y(64f))
            cubicTo(g.x(34f), g.y(60f), g.x(36f), g.y(56f), g.x(36f), g.y(46f))
            cubicTo(g.x(36f), g.y(34f), g.x(40f), g.y(26f), g.x(50f), g.y(26f))
            close()
        }
        part(tr, body, c.main)
        part(tr, g.roundRect(27f, 62f, 46f, 6f, 3f), c.main)
        part(tr, g.circle(50f - angle * 0.25f, 71f, 4.6f), c.main)
        part(tr, g.circle(50f, 23f, 3.2f), c.main)
        if (tr == null) {
            drawArc(
                c.onMain.copy(alpha = 0.4f), startAngle = 200f, sweepAngle = 50f, useCenter = false,
                topLeft = g.o(40f, 31f), size = g.s(20f, 26f), style = Stroke(2.2f * u, cap = StrokeCap.Round),
            )
        }
    }
    if (tr == null) {
        // A notification dot that pulses
        val pulse = 1f + 0.12f * sin(TAU * 2f * t)
        drawCircle(c.paper, radius = 7.2f * u * pulse, center = g.o(67f, 29f))
        drawCircle(c.accent, radius = 5.4f * u * pulse, center = g.o(67f, 29f))
    }
}

private fun DrawScope.clock(g: Grid, t: Float, c: ArtColors, tr: Tracer?) {
    val u = g.u
    // A little ring at the top of each turn
    val shake = 6f * sin(TAU * 9f * t) * bump(t, 0f, 0.16f)
    if (tr == null) drawOval(c.shadow, topLeft = g.o(30f, 84f), size = g.s(40f, 4f))
    rotate(shake, pivot = g.o(50f, 56f)) {
        // legs and bells behind the face
        val legL = g.line(36f, 76f, 31f, 84f)
        val legR = g.line(64f, 76f, 69f, 84f)
        if (tr != null) { tr.trace(this, legL); tr.trace(this, legR) } else {
            drawPath(legL, c.main, style = Stroke(3.4f * u, cap = StrokeCap.Round))
            drawPath(legR, c.main, style = Stroke(3.4f * u, cap = StrokeCap.Round))
        }
        part(tr, g.arc(23f, 22f, 41f, 40f, 180f, 180f, close = true), c.main)
        part(tr, g.arc(59f, 22f, 77f, 40f, 180f, 180f, close = true), c.main)
        part(tr, g.circle(50f, 56f, 26f), c.main)
        part(tr, g.circle(50f, 56f, 21.5f), c.paper)
        if (tr != null) return@rotate
        for (i in 0 until 12) {
            val a = i * TAU / 12f
            val r = if (i % 3 == 0) 1.6f else 1f
            drawCircle(c.ink.copy(alpha = 0.28f), radius = r * u, center = g.o(50f + cos(a) * 17f, 56f + sin(a) * 17f))
        }
        val minute = TAU * t - PI.toFloat() / 2f
        val hour = TAU * (0.33f + t / 12f) - PI.toFloat() / 2f
        drawLine(c.main, g.o(50f, 56f), g.o(50f + cos(hour) * 10f, 56f + sin(hour) * 10f), strokeWidth = 3.4f * u, cap = StrokeCap.Round)
        drawLine(c.ink.copy(alpha = 0.75f), g.o(50f, 56f), g.o(50f + cos(minute) * 15.5f, 56f + sin(minute) * 15.5f), strokeWidth = 2.2f * u, cap = StrokeCap.Round)
        drawCircle(c.ink.copy(alpha = 0.75f), radius = 2.4f * u, center = g.o(50f, 56f))
    }
}

private fun DrawScope.tea(g: Grid, t: Float, c: ArtColors, tr: Tracer?) {
    val u = g.u
    val saucer = Path().apply { addOval(Rect(g.x(24f), g.y(74f), g.x(76f), g.y(84f))) }
    part(tr, saucer, c.main.copy(alpha = 0.45f))
    val handle = g.arc(61f, 52f, 77f, 68f, -90f, 180f)
    if (tr != null) tr.trace(this, handle) else drawPath(handle, c.main, style = Stroke(3.6f * u, cap = StrokeCap.Round))
    val cup = Path().apply {
        moveTo(g.x(31f), g.y(48f))
        lineTo(g.x(69f), g.y(48f))
        cubicTo(g.x(69f), g.y(66f), g.x(62f), g.y(78f), g.x(50f), g.y(78f))
        cubicTo(g.x(38f), g.y(78f), g.x(31f), g.y(66f), g.x(31f), g.y(48f))
        close()
    }
    part(tr, cup, c.main)
    if (tr != null) return
    // the tea inside, and a little heart on the cup
    drawOval(Color(0xFFB8733A), topLeft = g.o(32.5f, 45.5f), size = g.s(35f, 6f))
    heart(g.o(50f, 63f), 3.6f * u, c.onMain.copy(alpha = 0.9f))
    for (i in 0..1) {
        val ph = (t + i * 0.5f) % 1f
        val x = 45f + i * 10f
        val rise = ph * 9f
        val alpha = sin(PI.toFloat() * ph) * 0.8f
        val wisp = Path().apply {
            moveTo(g.x(x), g.y(40f - rise))
            quadraticTo(g.x(x - 3f), g.y(36f - rise), g.x(x), g.y(32f - rise))
            quadraticTo(g.x(x + 3f), g.y(28f - rise), g.x(x), g.y(24f - rise))
        }
        drawPath(wisp, c.main.copy(alpha = alpha), style = Stroke(2.4f * u, cap = StrokeCap.Round))
    }
}

private fun DrawScope.shield(g: Grid, t: Float, c: ArtColors, tr: Tracer?) {
    val u = g.u
    val fade = 1f - clamp01((t - 0.9f) / 0.1f)
    if (tr == null) {
        // a calm pulse once the tick is in
        val q = clamp01((t - 0.42f) / 0.45f)
        if (q > 0f && q < 1f) {
            drawCircle(c.main.copy(alpha = (1f - q) * 0.45f), radius = (30f + 14f * q) * u, center = g.o(50f, 52f), style = Stroke(2.2f * u))
        }
        drawOval(c.shadow, topLeft = g.o(32f, 84f), size = g.s(36f, 4f))
    }
    val body = Path().apply {
        moveTo(g.x(50f), g.y(20f))
        cubicTo(g.x(58f), g.y(25f), g.x(66f), g.y(27f), g.x(73f), g.y(27f))
        lineTo(g.x(73f), g.y(50f))
        cubicTo(g.x(73f), g.y(66f), g.x(63f), g.y(76f), g.x(50f), g.y(83f))
        cubicTo(g.x(37f), g.y(76f), g.x(27f), g.y(66f), g.x(27f), g.y(50f))
        lineTo(g.x(27f), g.y(27f))
        cubicTo(g.x(34f), g.y(27f), g.x(42f), g.y(25f), g.x(50f), g.y(20f))
        close()
    }
    part(tr, body, c.main)
    if (tr != null) return
    clipRect(left = g.x(27f), top = g.y(18f), right = g.x(50f), bottom = g.y(86f)) { drawPath(body, c.onMain.copy(alpha = 0.12f)) }
    val tick = Path().apply {
        moveTo(g.x(39f), g.y(52f))
        lineTo(g.x(47f), g.y(60f))
        lineTo(g.x(62f), g.y(43f))
    }
    drawPartial(tick, easeOut(clamp01((t - 0.08f) / 0.32f)) * fade, c.onMain, 5f * u)
}

private fun DrawScope.heartbeat(g: Grid, t: Float, c: ArtColors, tr: Tracer?) {
    val u = g.u
    if (tr == null) {
        drawRoundRect(c.shadow, topLeft = g.o(20f, 35f), size = g.s(64f, 46f), cornerRadius = g.r(6f))
        drawRoundRect(c.ink.copy(alpha = 0.5f), topLeft = g.o(44f, 27f), size = g.s(12f, 8f), cornerRadius = g.r(2.5f))
    }
    part(tr, g.roundRect(18f, 32f, 64f, 46f, 6f), c.paper)
    part(tr, g.circle(31f, 47f, 7f), c.main.copy(alpha = 0.3f))
    val ecg = Path().apply {
        moveTo(g.x(24f), g.y(66f))
        lineTo(g.x(38f), g.y(66f))
        lineTo(g.x(42f), g.y(60f))
        lineTo(g.x(46f), g.y(72f))
        lineTo(g.x(50f), g.y(56f))
        lineTo(g.x(54f), g.y(68f))
        lineTo(g.x(58f), g.y(66f))
        lineTo(g.x(76f), g.y(66f))
    }
    if (tr != null) { tr.trace(this, ecg); return }
    // the person, two lines of details
    drawCircle(c.main, radius = 2.8f * u, center = g.o(31f, 45f))
    drawArc(c.main, 180f, 180f, true, topLeft = g.o(26f, 49f), size = g.s(10f, 8f))
    drawLine(c.ink.copy(alpha = 0.2f), g.o(44f, 43f), g.o(70f, 43f), strokeWidth = 2.4f * u, cap = StrokeCap.Round)
    drawLine(c.ink.copy(alpha = 0.13f), g.o(44f, 50f), g.o(62f, 50f), strokeWidth = 2.4f * u, cap = StrokeCap.Round)
    // a faint trace, with a bright pen running along it
    drawPath(ecg, c.main.copy(alpha = 0.18f), style = Stroke(2.2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
    val measure = PathMeasure().apply { setPath(ecg, false) }
    val head = (t * 1.35f) * measure.length
    val tail = head - measure.length * 0.32f
    val seg = Path()
    measure.getSegment(tail.coerceIn(0f, measure.length), head.coerceIn(0f, measure.length), seg, true)
    drawPath(seg, c.main, style = Stroke(2.6f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
    // the heart beats as the pen crosses the spike
    val beat = 1f + 0.22f * bump(t, 0.3f, 0.5f)
    heart(g.o(71f, 39f), 4.4f * u * beat, c.accent)
}

// ── More scenes (each place in the app has its own) ─────────────────────────

private fun heartPath(center: Offset, s: Float) = Path().apply {
    moveTo(center.x, center.y + s * 0.9f)
    cubicTo(center.x - s * 1.5f, center.y - s * 0.1f, center.x - s * 0.9f, center.y - s * 1.3f, center.x, center.y - s * 0.45f)
    cubicTo(center.x + s * 0.9f, center.y - s * 1.3f, center.x + s * 1.5f, center.y - s * 0.1f, center.x, center.y + s * 0.9f)
    close()
}

private fun DrawScope.star(center: Offset, r: Float, color: Color) {
    val p = Path()
    for (i in 0 until 10) {
        val a = -PI.toFloat() / 2f + i * PI.toFloat() / 5f
        val rr = if (i % 2 == 0) r else r * 0.45f
        val x = center.x + cos(a) * rr
        val y = center.y + sin(a) * rr
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    drawPath(p, color)
}

private fun gearPath(g: Grid, cx: Float, cy: Float, outer: Float, inner: Float, teeth: Int) = Path().apply {
    val n = teeth * 4
    for (i in 0 until n) {
        val a = TAU * i / n
        val r = if (i % 4 == 1 || i % 4 == 2) outer else inner
        val x = g.x(cx + cos(a) * r)
        val y = g.y(cy + sin(a) * r)
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** A weekly pill organiser: the lids open in a wave, showing a tablet in each day. */
private fun DrawScope.pillbox(g: Grid, t: Float, c: ArtColors, tr: Tracer?) {
    val u = g.u
    if (tr == null) drawOval(c.shadow, topLeft = g.o(16f, 78f), size = g.s(68f, 5f))
    part(tr, g.roundRect(14f, 50f, 72f, 28f, 5f), c.main)
    if (tr != null) {
        tr.trace(this, g.line(16f, 50f, 84f, 50f))
        return
    }
    val w = 8.4f
    val gap = 1.6f
    for (i in 0 until 7) {
        val x = 17f + i * (w + gap)
        drawRoundRect(c.paper, topLeft = g.o(x, 53f), size = g.s(w, 15f), cornerRadius = g.r(2f))
        val open = bump(t, 0.05f + i * 0.06f, 0.5f + i * 0.06f)
        if (open > 0.05f) drawCircle(c.accent.copy(alpha = open), radius = 2.4f * u, center = g.o(x + w / 2f, 61f))
        rotate(-75f * open, pivot = g.o(x, 52f)) {
            drawRoundRect(c.main, topLeft = g.o(x - 0.4f, 47.5f), size = g.s(w + 0.8f, 5f), cornerRadius = g.r(1.6f))
            drawRect(c.onMain.copy(alpha = 0.35f), topLeft = g.o(x + 1f, 48.6f), size = g.s(w - 2f, 1f))
        }
        drawCircle(c.onMain.copy(alpha = 0.8f), radius = 1.1f * u, center = g.o(x + w / 2f, 73f))
    }
}

/** A heart that beats (lub-dub), with dots circling it and a ring on each beat. */
private fun DrawScope.heartScene(g: Grid, t: Float, c: ArtColors, tr: Tracer?) {
    val u = g.u
    val beat = maxOf(
        maxOf(bump(t, 0.02f, 0.12f), 0.7f * bump(t, 0.14f, 0.24f)),
        maxOf(bump(t, 0.52f, 0.62f), 0.7f * bump(t, 0.64f, 0.74f)),
    )
    val s = 1f + 0.09f * beat
    if (tr == null) {
        for (k in 0..2) {
            val a = TAU * (k / 3f + t / 3f) - PI.toFloat() / 2f
            drawCircle(c.main.copy(alpha = 0.5f), radius = (2f + k * 0.5f) * u, center = g.o(50f + cos(a) * 37f, 52f + sin(a) * 35f))
        }
        val q = clamp01((t % 0.5f) / 0.45f)
        drawCircle(c.main.copy(alpha = (1f - q) * 0.3f), radius = (27f + 12f * q) * u, center = g.o(50f, 52f), style = Stroke(2f * u))
    }
    scale(s, s, pivot = g.o(50f, 52f)) {
        part(tr, heartPath(g.o(50f, 56f), 22f * u), c.main)
        if (tr != null) return@scale
        drawRoundRect(c.onMain, topLeft = g.o(47f, 42f), size = g.s(6f, 18f), cornerRadius = g.r(1.5f))
        drawRoundRect(c.onMain, topLeft = g.o(41f, 48f), size = g.s(18f, 6f), cornerRadius = g.r(1.5f))
        drawArc(
            c.onMain.copy(alpha = 0.35f), startAngle = 200f, sweepAngle = 55f, useCenter = false,
            topLeft = g.o(29f, 33f), size = g.s(22f, 22f), style = Stroke(2.4f * u, cap = StrokeCap.Round),
        )
    }
}

/** A phone that buzzes as a reminder slides in on its screen. */
private fun DrawScope.phone(g: Grid, t: Float, c: ArtColors, tr: Tracer?) {
    val u = g.u
    val buzz = bump(t, 0.12f, 0.36f)
    val wiggle = sin(TAU * 10f * t) * 4f * buzz
    if (tr == null && buzz > 0f) {
        drawArc(c.main.copy(alpha = 0.6f * buzz), 150f, 60f, false, topLeft = g.o(22f, 22f), size = g.s(56f, 56f), style = Stroke(2.4f * u, cap = StrokeCap.Round))
        drawArc(c.main.copy(alpha = 0.6f * buzz), -30f, 60f, false, topLeft = g.o(22f, 22f), size = g.s(56f, 56f), style = Stroke(2.4f * u, cap = StrokeCap.Round))
    }
    rotate(wiggle, pivot = g.o(50f, 50f)) {
        part(tr, g.roundRect(34f, 18f, 32f, 64f, 6f), c.main)
        part(tr, g.roundRect(37f, 25f, 26f, 49f, 2.5f), c.paper)
        if (tr != null) return@rotate
        drawRoundRect(c.onMain.copy(alpha = 0.6f), topLeft = g.o(45f, 21f), size = g.s(10f, 1.6f), cornerRadius = g.r(0.8f))
        drawRoundRect(c.onMain.copy(alpha = 0.6f), topLeft = g.o(45f, 77.5f), size = g.s(10f, 1.6f), cornerRadius = g.r(0.8f))
        drawRoundRect(c.main.copy(alpha = 0.2f), topLeft = g.o(41f, 44f), size = g.s(18f, 24f), cornerRadius = g.r(3f))
        drawRoundRect(c.main.copy(alpha = 0.5f), topLeft = g.o(45f, 52f), size = g.s(10f, 5f), cornerRadius = g.r(2.5f))
        val drop = easeOut(clamp01((t - 0.08f) / 0.18f))
        val fade = 1f - clamp01((t - 0.85f) / 0.12f)
        if (drop > 0f) {
            val y = 17f + 10f * drop
            clipRect(left = g.x(37f), top = g.y(25f), right = g.x(63f), bottom = g.y(74f)) {
                drawRoundRect(c.soft.copy(alpha = fade), topLeft = g.o(38.5f, y), size = g.s(23f, 11f), cornerRadius = g.r(2.5f))
                drawCircle(c.main.copy(alpha = fade), radius = 2.6f * u, center = g.o(43f, y + 5.5f))
                drawLine(c.ink.copy(alpha = 0.35f * fade), g.o(47.5f, y + 4f), g.o(59f, y + 4f), strokeWidth = 1.6f * u, cap = StrokeCap.Round)
                drawLine(c.ink.copy(alpha = 0.2f * fade), g.o(47.5f, y + 7.5f), g.o(55f, y + 7.5f), strokeWidth = 1.6f * u, cap = StrokeCap.Round)
            }
        }
    }
}

/** A magnifying glass gliding over a medicine box; a sparkle when it finds the cross. */
private fun DrawScope.search(g: Grid, t: Float, c: ArtColors, tr: Tracer?) {
    val u = g.u
    if (tr == null) drawOval(c.shadow, topLeft = g.o(22f, 76f), size = g.s(46f, 4f))
    val box = g.roundRect(22f, 36f, 46f, 40f, 4f)
    part(tr, box, c.paper)
    if (tr == null) {
        drawPath(box, c.main.copy(alpha = 0.3f), style = Stroke(1.2f * u))
        drawRect(c.main, topLeft = g.o(22f, 44f), size = g.s(46f, 7f))
        drawRoundRect(c.main, topLeft = g.o(41.5f, 55f), size = g.s(7f, 15f), cornerRadius = g.r(1f))
        drawRoundRect(c.main, topLeft = g.o(37.5f, 59f), size = g.s(15f, 7f), cornerRadius = g.r(1f))
    }
    val x = 45f + 18f * sin(TAU * t)
    val y = 55f + 3f * sin(TAU * 2f * t)
    if (tr == null) drawCircle(c.soft.copy(alpha = 0.55f), radius = 10f * u, center = g.o(x, y))
    val lens = g.circle(x, y, 11f)
    val handle = g.line(x + 8f, y + 8f, x + 17f, y + 17f)
    if (tr != null) {
        tr.trace(this, lens)
        tr.trace(this, handle)
        return
    }
    drawPath(lens, c.main, style = Stroke(3.4f * u))
    drawPath(handle, c.main, style = Stroke(5f * u, cap = StrokeCap.Round))
    drawArc(c.paper.copy(alpha = 0.85f), 200f, 50f, false, topLeft = g.o(x - 7f, y - 7f), size = g.s(14f, 14f), style = Stroke(1.8f * u, cap = StrokeCap.Round))
    val found = bump(t, 0.42f, 0.58f)
    if (found > 0f) sparkle(g.o(x + 9f, y - 13f), 4.5f * u * found, c.accent.copy(alpha = found))
}

/** A medicine box that gets a check stamp — "yes, this one". */
private fun DrawScope.boxCheck(g: Grid, t: Float, c: ArtColors, tr: Tracer?) {
    val u = g.u
    val fade = 1f - clamp01((t - 0.9f) / 0.1f)
    val stamp = easeOutBack(clamp01((t - 0.15f) / 0.25f)) * fade
    val tick = easeOut(clamp01((t - 0.35f) / 0.25f)) * fade
    val bounce = 1f + 0.05f * bump(t, 0.15f, 0.35f)
    if (tr == null) drawOval(c.shadow, topLeft = g.o(24f, 79f), size = g.s(58f, 5f))
    scale(bounce, bounce, pivot = g.o(52f, 80f)) {
        val top = Path().apply {
            moveTo(g.x(24f), g.y(42f)); lineTo(g.x(32f), g.y(33f)); lineTo(g.x(80f), g.y(33f)); lineTo(g.x(72f), g.y(42f)); close()
        }
        val side = Path().apply {
            moveTo(g.x(72f), g.y(42f)); lineTo(g.x(80f), g.y(33f)); lineTo(g.x(80f), g.y(71f)); lineTo(g.x(72f), g.y(80f)); close()
        }
        part(tr, top, c.main.copy(alpha = 0.75f))
        part(tr, side, c.main.copy(alpha = 0.55f))
        part(tr, g.rect(24f, 42f, 48f, 38f), c.main)
        if (tr != null) return@scale
        drawRoundRect(c.paper, topLeft = g.o(29f, 49f), size = g.s(38f, 20f), cornerRadius = g.r(2f))
        drawLine(c.ink.copy(alpha = 0.25f), g.o(33f, 55f), g.o(58f, 55f), strokeWidth = 2.2f * u, cap = StrokeCap.Round)
        drawLine(c.ink.copy(alpha = 0.15f), g.o(33f, 61f), g.o(51f, 61f), strokeWidth = 2.2f * u, cap = StrokeCap.Round)
        if (stamp > 0f) {
            scale(stamp, stamp, pivot = g.o(68f, 70f)) {
                drawCircle(c.paper, radius = 12f * u, center = g.o(68f, 70f))
                drawCircle(c.main, radius = 12f * u, center = g.o(68f, 70f), style = Stroke(3f * u))
            }
            val tickPath = Path().apply { moveTo(g.x(62f), g.y(70f)); lineTo(g.x(66.5f), g.y(74.5f)); lineTo(g.x(74.5f), g.y(65.5f)) }
            drawPartial(tickPath, tick, c.main, 3.6f * u)
        }
    }
}

/** The sun crosses the sky and sets; the moon rises and the stars come out. */
private fun DrawScope.sunMoon(g: Grid, t: Float, c: ArtColors, tr: Tracer?) {
    val u = g.u
    val half = (t * 2f) % 1f
    val isSun = t < 0.5f
    val a = PI.toFloat() * (1f - half)
    val cx = 50f + cos(a) * 30f
    val cy = 66f - sin(a) * 36f
    val hill = Path().apply {
        moveTo(g.x(8f), g.y(70f))
        cubicTo(g.x(30f), g.y(60f), g.x(70f), g.y(60f), g.x(92f), g.y(70f))
        lineTo(g.x(92f), g.y(96f)); lineTo(g.x(8f), g.y(96f)); close()
    }
    val backdrop = g.circle(50f, 52f, 44f)
    if (tr != null) {
        tr.trace(this, g.circle(cx, cy, 9f))
        clipPath(backdrop) { tr.trace(this, hill) }
        return
    }
    if (!isSun) {
        val night = bump(half, 0f, 1f)
        listOf(30f to 30f, 42f to 22f, 66f to 26f, 74f to 42f, 24f to 46f).forEachIndexed { i, (x, y) ->
            val tw = 0.4f + 0.6f * (0.5f + 0.5f * sin(TAU * (t * 3f + i * 0.2f)))
            drawCircle(c.main.copy(alpha = 0.7f * tw * night), radius = 1.4f * u, center = g.o(x, y))
        }
        val disc = g.circle(cx, cy, 9f)
        val bite = g.circle(cx + 4.5f, cy - 3f, 8f)
        drawPath(Path.combine(PathOperation.Difference, disc, bite), c.main)
    } else {
        val sun = Color(0xFFFFB547)
        drawCircle(sun.copy(alpha = 0.3f), radius = 13f * u, center = g.o(cx, cy))
        drawCircle(sun, radius = 9f * u, center = g.o(cx, cy))
    }
    clipPath(backdrop) { drawPath(hill, lerp(c.soft, c.main, 0.45f)) }
}

/** Tablets drop into three slots, then the day's limit bar fills and glows. */
private fun DrawScope.counter(g: Grid, t: Float, c: ArtColors, tr: Tracer?) {
    val u = g.u
    val fade = 1f - clamp01((t - 0.9f) / 0.1f)
    if (tr == null) drawOval(c.shadow, topLeft = g.o(22f, 76f), size = g.s(56f, 4f))
    val card = g.roundRect(20f, 32f, 60f, 42f, 6f)
    part(tr, card, c.paper)
    val xs = listOf(34f, 50f, 66f)
    xs.forEach { x ->
        val ring = g.circle(x, 48f, 7f)
        if (tr != null) tr.trace(this, ring) else drawPath(ring, c.main.copy(alpha = 0.35f), style = Stroke(1.8f * u))
    }
    if (tr != null) return
    drawPath(card, c.main.copy(alpha = 0.3f), style = Stroke(1.2f * u))
    xs.forEachIndexed { i, x ->
        val land = clamp01((t - 0.08f - i * 0.17f) / 0.14f)
        if (land > 0f) {
            val y = 48f - 20f * (1f - easeOutBack(land))
            drawCircle(c.main.copy(alpha = fade), radius = 5.6f * u, center = g.o(x, y))
            drawCircle(c.onMain.copy(alpha = 0.35f * fade), radius = 1.6f * u, center = g.o(x - 1.8f, y - 1.8f))
        }
    }
    val fill = easeOut(clamp01((t - 0.6f) / 0.18f)) * fade
    drawRoundRect(c.main.copy(alpha = 0.18f), topLeft = g.o(28f, 62f), size = g.s(44f, 5f), cornerRadius = g.r(2.5f))
    if (fill > 0f) drawRoundRect(c.main.copy(alpha = fade), topLeft = g.o(28f, 62f), size = g.s(44f * fill, 5f), cornerRadius = g.r(2.5f))
    val glow = bump(t, 0.76f, 0.9f)
    if (glow > 0f) drawRoundRect(c.main.copy(alpha = 0.3f * glow), topLeft = g.o(25f, 59f), size = g.s(50f, 11f), cornerRadius = g.r(5.5f))
}

/** A stethoscope listening: the chest piece swings, rings and a heart beat. */
private fun DrawScope.stethoscope(g: Grid, t: Float, c: ArtColors, tr: Tracer?) {
    val u = g.u
    val swing = 8f * sin(TAU * t)
    val beat = maxOf(
        maxOf(bump(t, 0.1f, 0.2f), 0.7f * bump(t, 0.22f, 0.32f)),
        maxOf(bump(t, 0.6f, 0.7f), 0.7f * bump(t, 0.72f, 0.82f)),
    )
    val tubeL = Path().apply { moveTo(g.x(34f), g.y(22f)); cubicTo(g.x(30f), g.y(40f), g.x(40f), g.y(52f), g.x(50f), g.y(54f)) }
    val tubeR = Path().apply { moveTo(g.x(66f), g.y(22f)); cubicTo(g.x(70f), g.y(40f), g.x(60f), g.y(52f), g.x(50f), g.y(54f)) }
    if (tr != null) {
        tr.trace(this, tubeL)
        tr.trace(this, tubeR)
    } else {
        drawPath(tubeL, c.main, style = Stroke(3.6f * u, cap = StrokeCap.Round))
        drawPath(tubeR, c.main, style = Stroke(3.6f * u, cap = StrokeCap.Round))
        drawCircle(c.main, radius = 3f * u, center = g.o(34f, 21f))
        drawCircle(c.main, radius = 3f * u, center = g.o(66f, 21f))
    }
    rotate(swing, pivot = g.o(50f, 54f)) {
        val tube = Path().apply { moveTo(g.x(50f), g.y(54f)); cubicTo(g.x(50f), g.y(66f), g.x(62f), g.y(63f), g.x(64f), g.y(69f)) }
        if (tr != null) {
            tr.trace(this, tube)
            tr.trace(this, g.circle(66f, 76f, 8.5f))
            return@rotate
        }
        drawPath(tube, c.main, style = Stroke(3.6f * u, cap = StrokeCap.Round))
        drawCircle(c.main, radius = 8.5f * u, center = g.o(66f, 76f))
        drawCircle(c.paper, radius = 5f * u, center = g.o(66f, 76f))
        if (beat > 0f) drawCircle(c.main.copy(alpha = 0.5f * beat), radius = (11f + 6f * beat) * u, center = g.o(66f, 76f), style = Stroke(2f * u))
    }
    if (tr == null) drawPath(heartPath(g.o(77f, 44f), (3.6f + 1.4f * beat) * u), c.accent)
}

/** A gold medal that swings on its ribbon, with a shine sweeping across. */
private fun DrawScope.medal(g: Grid, t: Float, c: ArtColors, tr: Tracer?) {
    val u = g.u
    val swing = 6f * sin(TAU * t)
    rotate(swing, pivot = g.o(50f, 18f)) {
        val ribbonL = Path().apply { moveTo(g.x(38f), g.y(16f)); lineTo(g.x(48f), g.y(16f)); lineTo(g.x(55f), g.y(44f)); lineTo(g.x(46f), g.y(46f)); close() }
        val ribbonR = Path().apply { moveTo(g.x(62f), g.y(16f)); lineTo(g.x(52f), g.y(16f)); lineTo(g.x(45f), g.y(44f)); lineTo(g.x(54f), g.y(46f)); close() }
        part(tr, ribbonL, c.accent)
        part(tr, ribbonR, c.accent.copy(alpha = 0.8f))
        val disc = g.circle(50f, 60f, 17f)
        part(tr, disc, c.main)
        if (tr != null) return@rotate
        drawCircle(c.onMain.copy(alpha = 0.35f), radius = 13f * u, center = g.o(50f, 60f), style = Stroke(1.6f * u))
        star(g.o(50f, 60f), 8.5f * u, c.onMain)
        val sweep = clamp01((t - 0.15f) / 0.3f)
        if (sweep > 0.001f && sweep < 0.999f) {
            clipPath(disc) {
                val x = 30f + 40f * sweep
                drawLine(c.onMain.copy(alpha = 0.5f), g.o(x - 6f, 78f), g.o(x + 6f, 42f), strokeWidth = 4f * u, cap = StrokeCap.Round)
            }
        }
    }
    if (tr == null) {
        sparkle(g.o(24f, 54f), 4f * u, c.main.copy(alpha = twinkle(t, 0.2f)))
        sparkle(g.o(76f, 70f), 3.2f * u, c.main.copy(alpha = twinkle(t, 0.7f)))
    }
}

/** Two gears turning together (one tooth each per loop, so it repeats seamlessly). */
private fun DrawScope.gears(g: Grid, t: Float, c: ArtColors, tr: Tracer?) {
    val u = g.u
    rotate(36f * t, pivot = g.o(42f, 56f)) {
        part(tr, gearPath(g, 42f, 56f, 21f, 16.5f, 10), c.main)
        if (tr == null) {
            drawCircle(c.onMain.copy(alpha = 0.3f), radius = 11f * u, center = g.o(42f, 56f), style = Stroke(1.5f * u))
            drawCircle(c.soft, radius = 6f * u, center = g.o(42f, 56f))
        }
    }
    rotate(-(360f / 7f) * t + 360f / 14f, pivot = g.o(68f, 36f)) {
        part(tr, gearPath(g, 68f, 36f, 14f, 10.5f, 7), c.main.copy(alpha = 0.75f))
        if (tr == null) drawCircle(c.soft, radius = 4f * u, center = g.o(68f, 36f))
    }
}

/** A gift box whose lid pops up, letting out sparkles — a new version. */
private fun DrawScope.gift(g: Grid, t: Float, c: ArtColors, tr: Tracer?) {
    val u = g.u
    val open = easeOut(clamp01((t - 0.08f) / 0.14f)) - smooth(clamp01((t - 0.65f) / 0.15f))
    if (tr == null) {
        drawOval(c.shadow, topLeft = g.o(26f, 80f), size = g.s(48f, 5f))
        if (open > 0f) for (k in 0..2) {
            val p = clamp01((t - 0.18f - k * 0.08f) / 0.4f)
            if (p > 0.001f && p < 0.999f) {
                sparkle(g.o(40f + k * 10f, 46f - 28f * p), (3f + k) * u, (if (k == 1) c.accent else c.main).copy(alpha = 1f - p))
            }
        }
    }
    part(tr, g.roundRect(28f, 52f, 44f, 30f, 3f), c.main)
    if (tr == null) drawRect(c.accent, topLeft = g.o(47f, 52f), size = g.s(6f, 30f))
    translate(top = -10f * open * u) {
        rotate(-10f * open, pivot = g.o(25f, 52f)) {
            part(tr, g.roundRect(25f, 43f, 50f, 10f, 3f), c.main)
            if (tr != null) return@rotate
            drawRect(c.onMain.copy(alpha = 0.18f), topLeft = g.o(25f, 49f), size = g.s(50f, 1.6f))
            drawRect(c.accent, topLeft = g.o(47f, 43f), size = g.s(6f, 10f))
            drawOval(c.accent, topLeft = g.o(38f, 35f), size = g.s(12f, 9f))
            drawOval(c.accent, topLeft = g.o(50f, 35f), size = g.s(12f, 9f))
            drawCircle(c.accent, radius = 2.6f * u, center = g.o(50f, 42f))
        }
    }
}

/** Bars grow one after another, then a trend line draws across their tops. */
private fun DrawScope.chart(g: Grid, t: Float, c: ArtColors, tr: Tracer?) {
    val u = g.u
    val fade = 1f - clamp01((t - 0.9f) / 0.1f)
    val card = g.roundRect(18f, 26f, 64f, 54f, 6f)
    part(tr, card, c.paper)
    val axes = Path().apply { moveTo(g.x(26f), g.y(32f)); lineTo(g.x(26f), g.y(72f)); lineTo(g.x(76f), g.y(72f)) }
    if (tr != null) {
        tr.trace(this, axes)
        return
    }
    drawPath(card, c.main.copy(alpha = 0.25f), style = Stroke(1.2f * u))
    drawPath(axes, c.ink.copy(alpha = 0.3f), style = Stroke(1.8f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
    val heights = listOf(14f, 24f, 19f, 32f)
    val tops = mutableListOf<Offset>()
    heights.forEachIndexed { i, h ->
        val grow = easeOutBack(clamp01((t - 0.05f - i * 0.1f) / 0.22f)) * fade
        val x = 32f + i * 11f
        val hh = h * grow
        if (hh > 0.2f) drawRoundRect(c.main, topLeft = g.o(x, 71f - hh), size = g.s(7f, hh), cornerRadius = g.r(1.6f))
        tops += g.o(x + 3.5f, 71f - h - 4f)
    }
    val line = Path().apply { tops.forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) } }
    val lp = easeOut(clamp01((t - 0.5f) / 0.25f)) * fade
    drawPartial(line, lp, c.accent, 2.4f * u)
    if (lp > 0.98f) tops.forEach { drawCircle(c.accent, radius = 2f * u, center = it) }
}
