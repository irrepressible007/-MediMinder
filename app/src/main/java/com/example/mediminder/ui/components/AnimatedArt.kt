package com.example.mediminder.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.mediminder.ui.theme.Hue
import com.example.mediminder.ui.theme.LocalDarkTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/*
 * Small animated scenes for empty screens and the first-run pages. Each one loops gently every
 * few seconds; with "Remove animations" on, it shows one well-chosen still frame instead.
 * Everything is drawn on a 100 × 100 grid and scaled to the size asked for.
 */

enum class Art(internal val periodMillis: Int, internal val still: Float) {
    /** A pill bottle with a capsule and a tablet hopping beside it. */
    PILLS(2400, 0f),
    /** A calendar: a day gets circled and a heart pops in. */
    CALENDAR(3200, 0.72f),
    /** A clipboard whose ticks draw themselves one by one. */
    CHECKLIST(3600, 0.84f),
    /** A shopping bag that gets a tick and bounces. */
    BAG(2800, 0.62f),
    /** A folder with a paper sliding in and out. */
    FOLDER(3200, 0.5f),
    /** A bowl of rice with steam rising. */
    MEAL(3000, 0.3f),
    /** A bell swinging with sound waves. */
    BELL(2400, 0.12f),
}

@Composable
fun AnimatedArt(art: Art, hue: Hue, modifier: Modifier = Modifier, size: Dp = 176.dp) {
    val t = if (rememberReduceMotion()) art.still else {
        val inf = rememberInfiniteTransition(label = "art")
        inf.animateFloat(0f, 1f, infiniteRepeatable(tween(art.periodMillis, easing = LinearEasing)), label = "t").value
    }
    ArtFrame(art, hue, t, modifier.size(size))
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

/** One frame of [art] at time [t] (0..1 through its loop). */
@Composable
internal fun ArtFrame(art: Art, hue: Hue, t: Float, modifier: Modifier) {
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
    Canvas(modifier) {
        val u = size.minDimension / 100f
        val g = Grid(u, (size.width - 100f * u) / 2f, (size.height - 100f * u) / 2f)
        // Backdrop: a soft blob with a few twinkling sparkles
        drawCircle(c.soft, radius = 44f * u, center = g.o(50f, 52f))
        sparkle(g.o(13f, 22f), 5.5f * u, c.main.copy(alpha = twinkle(t, 0f)))
        sparkle(g.o(89f, 36f), 3.8f * u, c.main.copy(alpha = twinkle(t, 0.45f)))
        drawCircle(c.main.copy(alpha = 0.22f), radius = 2.4f * u, center = g.o(86f, 82f))
        drawCircle(c.main.copy(alpha = 0.16f), radius = 1.8f * u, center = g.o(10f, 70f))
        when (art) {
            Art.PILLS -> pills(g, t, c)
            Art.CALENDAR -> calendar(g, t, c)
            Art.CHECKLIST -> checklist(g, t, c)
            Art.BAG -> bag(g, t, c)
            Art.FOLDER -> folder(g, t, c)
            Art.MEAL -> meal(g, t, c)
            Art.BELL -> bell(g, t, c)
        }
    }
}

/** Grid units → pixels. */
private class Grid(val u: Float, private val ox: Float, private val oy: Float) {
    fun x(v: Float) = ox + v * u
    fun y(v: Float) = oy + v * u
    fun o(x: Float, y: Float) = Offset(x(x), y(y))
    fun s(w: Float, h: Float) = Size(w * u, h * u)
    fun r(v: Float) = CornerRadius(v * u)
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

/** Draws the first [fraction] of [path] as a line — for ticks that write themselves. */
private fun DrawScope.drawPartial(path: Path, fraction: Float, color: Color, width: Float) {
    if (fraction <= 0f) return
    val measure = PathMeasure().apply { setPath(path, false) }
    val part = Path()
    measure.getSegment(0f, measure.length * clamp01(fraction), part, true)
    drawPath(part, color, style = Stroke(width, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
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

private fun DrawScope.pills(g: Grid, t: Float, c: ArtColors) {
    val u = g.u
    // The bottle sways a touch
    drawOval(c.shadow, topLeft = g.o(27f, 82f), size = g.s(32f, 5f))
    rotate(sin(TAU * t) * 2.5f, pivot = g.o(43f, 84f)) {
        drawRoundRect(c.paper, topLeft = g.o(29f, 40f), size = g.s(28f, 44f), cornerRadius = g.r(7f))
        drawRect(c.main, topLeft = g.o(29f, 54f), size = g.s(28f, 16f))
        drawRect(c.onMain, topLeft = g.o(41.5f, 57.5f), size = g.s(3f, 9f))
        drawRect(c.onMain, topLeft = g.o(38.5f, 60.5f), size = g.s(9f, 3f))
        drawRoundRect(c.main, topLeft = g.o(31f, 30f), size = g.s(24f, 11f), cornerRadius = g.r(3f))
        for (i in 0..3) drawRect(c.onMain.copy(alpha = 0.35f), topLeft = g.o(34.5f + i * 5.2f, 32f), size = g.s(1.6f, 7f))
    }

    // A capsule hops and flips once per loop
    val h = abs(sin(PI.toFloat() * t))
    val cx = 72f
    val cy = 84f - 5f - h * 24f
    drawOval(c.shadow.copy(alpha = c.shadow.alpha * (1f - 0.6f * h)), topLeft = g.o(cx - (9f - 4f * h), 82.5f), size = g.s(18f - 8f * h, 3.5f))
    val squash = if (h < 0.12f) 0.18f * (1f - h / 0.12f) else 0f
    rotate(360f * smooth(t), pivot = g.o(cx, cy)) {
        scale(1f + squash, 1f - squash, pivot = g.o(cx, cy + 5f)) {
            drawRoundRect(c.paper, topLeft = g.o(cx - 11f, cy - 5f), size = g.s(22f, 10f), cornerRadius = g.r(5f))
            clipRect(left = g.x(cx - 11f), top = g.y(cy - 6f), right = g.x(cx), bottom = g.y(cy + 6f)) {
                drawRoundRect(c.main, topLeft = g.o(cx - 11f, cy - 5f), size = g.s(22f, 10f), cornerRadius = g.r(5f))
            }
            drawRoundRect(c.main.copy(alpha = 0.45f), topLeft = g.o(cx - 11f, cy - 5f), size = g.s(22f, 10f), cornerRadius = g.r(5f), style = Stroke(1f * u))
        }
    }

    // A round tablet hops in between
    val h2 = abs(sin(PI.toFloat() * ((t + 0.5f) % 1f)))
    val tx = 87f
    val ty = 84f - 5f - h2 * 13f
    drawOval(c.shadow.copy(alpha = c.shadow.alpha * (1f - 0.6f * h2)), topLeft = g.o(tx - (5f - 2f * h2), 82.5f), size = g.s(10f - 4f * h2, 3f))
    // a plain white tablet (no outline or slash, so it never reads as a "no" sign)
    drawCircle(c.paper, radius = 5.2f * u, center = g.o(tx, ty))
    drawCircle(c.main.copy(alpha = 0.18f), radius = 5.2f * u, center = g.o(tx + 0.8f, ty + 0.9f), style = Stroke(0.9f * u))
    drawLine(c.main.copy(alpha = 0.3f), g.o(tx - 2.4f, ty), g.o(tx + 2.4f, ty), strokeWidth = 0.9f * u, cap = StrokeCap.Round)
}

private fun DrawScope.calendar(g: Grid, t: Float, c: ArtColors) {
    val u = g.u
    val fade = 1f - clamp01((t - 0.86f) / 0.14f)
    translate(top = sin(TAU * t) * 1.5f * u) {
        drawRoundRect(c.shadow, topLeft = g.o(25f, 33f), size = g.s(54f, 52f), cornerRadius = g.r(6f))
        drawRoundRect(c.paper, topLeft = g.o(23f, 30f), size = g.s(54f, 52f), cornerRadius = g.r(6f))
        clipRect(left = g.x(23f), top = g.y(30f), right = g.x(77f), bottom = g.y(44f)) {
            drawRoundRect(c.main, topLeft = g.o(23f, 30f), size = g.s(54f, 52f), cornerRadius = g.r(6f))
        }
        drawRoundRect(c.ink.copy(alpha = 0.55f), topLeft = g.o(34f, 24f), size = g.s(4f, 11f), cornerRadius = g.r(2f))
        drawRoundRect(c.ink.copy(alpha = 0.55f), topLeft = g.o(62f, 24f), size = g.s(4f, 11f), cornerRadius = g.r(2f))
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

private fun DrawScope.checklist(g: Grid, t: Float, c: ArtColors) {
    val u = g.u
    val fade = 1f - clamp01((t - 0.9f) / 0.1f)
    drawRoundRect(c.shadow, topLeft = g.o(29f, 27f), size = g.s(46f, 60f), cornerRadius = g.r(6f))
    drawRoundRect(c.main, topLeft = g.o(27f, 24f), size = g.s(46f, 60f), cornerRadius = g.r(6f))
    drawRoundRect(c.paper, topLeft = g.o(31f, 31f), size = g.s(38f, 49f), cornerRadius = g.r(3f))
    drawRoundRect(c.ink.copy(alpha = 0.6f), topLeft = g.o(41f, 20f), size = g.s(18f, 8f), cornerRadius = g.r(3f))
    for (i in 0..2) {
        val y = 43f + i * 12f
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

private fun DrawScope.bag(g: Grid, t: Float, c: ArtColors) {
    val u = g.u
    val fade = 1f - clamp01((t - 0.88f) / 0.12f)
    val p = easeOut(clamp01((t - 0.08f) / 0.32f)) * fade
    val bounce = 1f + 0.08f * bump(t, 0.38f, 0.58f)
    drawOval(c.shadow, topLeft = g.o(29f, 82f), size = g.s(42f, 5f))
    scale(bounce, bounce, pivot = g.o(50f, 84f)) {
        drawArc(
            c.main, startAngle = 180f, sweepAngle = 180f, useCenter = false,
            topLeft = g.o(39f, 27f), size = g.s(22f, 26f), style = Stroke(3.4f * u, cap = StrokeCap.Round),
        )
        drawRoundRect(c.main, topLeft = g.o(28f, 40f), size = g.s(44f, 44f), cornerRadius = g.r(7f))
        drawRect(c.onMain.copy(alpha = 0.22f), topLeft = g.o(28f, 46f), size = g.s(44f, 3f))
        val tick = Path().apply {
            moveTo(g.x(40f), g.y(64f))
            lineTo(g.x(47f), g.y(71f))
            lineTo(g.x(61f), g.y(56f))
        }
        drawPartial(tick, p, c.onMain, 4.2f * u)
    }
}

private fun DrawScope.folder(g: Grid, t: Float, c: ArtColors) {
    val u = g.u
    val out = 0.5f - 0.5f * cos(TAU * t)
    val paperY = 47f - 21f * smooth(out)
    drawOval(c.shadow, topLeft = g.o(26f, 81f), size = g.s(50f, 5f))
    drawRoundRect(c.main.copy(alpha = 0.6f), topLeft = g.o(24f, 31f), size = g.s(20f, 10f), cornerRadius = g.r(3f))
    drawRoundRect(c.main.copy(alpha = 0.6f), topLeft = g.o(24f, 36f), size = g.s(52f, 47f), cornerRadius = g.r(5f))
    // The paper, with a little picture and lines on it
    rotate(-4f * out, pivot = g.o(50f, paperY + 17f)) {
        drawRoundRect(c.paper, topLeft = g.o(31f, paperY), size = g.s(38f, 34f), cornerRadius = g.r(2.5f))
        drawRoundRect(c.main.copy(alpha = 0.35f), topLeft = g.o(35f, paperY + 5f), size = g.s(9f, 9f), cornerRadius = g.r(1.5f))
        drawLine(c.ink.copy(alpha = 0.16f), g.o(48f, paperY + 7f), g.o(64f, paperY + 7f), strokeWidth = 2f * u, cap = StrokeCap.Round)
        drawLine(c.ink.copy(alpha = 0.12f), g.o(48f, paperY + 12f), g.o(60f, paperY + 12f), strokeWidth = 2f * u, cap = StrokeCap.Round)
        drawLine(c.ink.copy(alpha = 0.12f), g.o(35f, paperY + 19f), g.o(64f, paperY + 19f), strokeWidth = 2f * u, cap = StrokeCap.Round)
    }
    drawRoundRect(c.main, topLeft = g.o(24f, 51f), size = g.s(52f, 32f), cornerRadius = g.r(5f))
    drawRect(c.onMain.copy(alpha = 0.18f), topLeft = g.o(24f, 54f), size = g.s(52f, 2f))
}

private fun DrawScope.meal(g: Grid, t: Float, c: ArtColors) {
    val u = g.u
    drawOval(c.shadow, topLeft = g.o(30f, 81f), size = g.s(40f, 5f))
    drawRoundRect(c.main, topLeft = g.o(42f, 77f), size = g.s(16f, 6f), cornerRadius = g.r(2f))
    drawArc(c.main, startAngle = 0f, sweepAngle = 180f, useCenter = true, topLeft = g.o(26f, 32f), size = g.s(48f, 48f))
    // A mound of rice
    drawArc(c.paper, startAngle = 180f, sweepAngle = 180f, useCenter = true, topLeft = g.o(29f, 42f), size = g.s(42f, 28f))
    listOf(38f to 50f, 46f to 47f, 55f to 49f, 62f to 52f, 50f to 52f).forEach { (x, y) ->
        drawOval(c.ink.copy(alpha = 0.08f), topLeft = g.o(x, y), size = g.s(3.2f, 1.6f))
    }
    drawRoundRect(c.main, topLeft = g.o(24f, 54f), size = g.s(52f, 5f), cornerRadius = g.r(2.5f))
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

private fun DrawScope.bell(g: Grid, t: Float, c: ArtColors) {
    val u = g.u
    // Sound waves on both sides
    for (k in 0..1) {
        val p = (t * 2f + k * 0.5f) % 1f
        val r = 25f + 10f * p
        val a = (1f - p) * 0.75f
        drawArc(c.main.copy(alpha = a), 145f, 70f, false, topLeft = g.o(50f - r, 47f - r), size = g.s(2 * r, 2 * r), style = Stroke(2.4f * u, cap = StrokeCap.Round))
        drawArc(c.main.copy(alpha = a), -35f, 70f, false, topLeft = g.o(50f - r, 47f - r), size = g.s(2 * r, 2 * r), style = Stroke(2.4f * u, cap = StrokeCap.Round))
    }
    drawOval(c.shadow, topLeft = g.o(34f, 82f), size = g.s(32f, 4f))
    val angle = 13f * sin(TAU * 2f * t) * (0.65f + 0.35f * cos(TAU * t))
    rotate(angle, pivot = g.o(50f, 22f)) {
        drawCircle(c.main, radius = 3.2f * u, center = g.o(50f, 23f))
        val body = Path().apply {
            moveTo(g.x(50f), g.y(26f))
            cubicTo(g.x(60f), g.y(26f), g.x(64f), g.y(34f), g.x(64f), g.y(46f))
            cubicTo(g.x(64f), g.y(56f), g.x(66f), g.y(60f), g.x(70f), g.y(64f))
            lineTo(g.x(30f), g.y(64f))
            cubicTo(g.x(34f), g.y(60f), g.x(36f), g.y(56f), g.x(36f), g.y(46f))
            cubicTo(g.x(36f), g.y(34f), g.x(40f), g.y(26f), g.x(50f), g.y(26f))
            close()
        }
        drawPath(body, c.main)
        drawArc(
            c.onMain.copy(alpha = 0.4f), startAngle = 200f, sweepAngle = 50f, useCenter = false,
            topLeft = g.o(40f, 31f), size = g.s(20f, 26f), style = Stroke(2.2f * u, cap = StrokeCap.Round),
        )
        drawRoundRect(c.main, topLeft = g.o(27f, 62f), size = g.s(46f, 6f), cornerRadius = g.r(3f))
        drawCircle(c.main, radius = 4.6f * u, center = g.o(50f - angle * 0.25f, 71f))
    }
    // A notification dot that pulses
    val pulse = 1f + 0.12f * sin(TAU * 2f * t)
    drawCircle(c.paper, radius = 7.2f * u * pulse, center = g.o(67f, 29f))
    drawCircle(c.accent, radius = 5.4f * u * pulse, center = g.o(67f, 29f))
}
