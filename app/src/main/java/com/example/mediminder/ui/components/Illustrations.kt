package com.example.mediminder.ui.components

import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.mediminder.ui.theme.Hue
import com.example.mediminder.ui.theme.color
import com.example.mediminder.ui.theme.soft
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/*
 * Pictures drawn in code: crisp at any size, light in the APK, coloured from the theme, and able
 * to move. They give each part of the app a face, so screens are not just text and boxes.
 */

// ── Icon tiles ──────────────────────────────────────────────────────────────

/** A rounded tile with a soft tint and a strong icon — each feature gets its own colour. */
@Composable
fun IconBadge(icon: ImageVector, hue: Hue, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(hue.soft),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = hue.color, modifier = Modifier.size(size * 0.54f))
    }
}

/**
 * A friendly picture for empty screens and first pages: the icon on a white disc, sitting on a
 * soft blob of its colour, with a few sparkles that twinkle.
 */
@Composable
fun SpotIllustration(icon: ImageVector, hue: Hue, modifier: Modifier = Modifier, size: Dp = 136.dp) {
    val fg = hue.color
    val bg = hue.soft
    val disc = MaterialTheme.colorScheme.surface
    val reduce = rememberReduceMotion()
    val phase = if (reduce) 0.25f else {
        val t = rememberInfiniteTransition(label = "sparkle")
        t.animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "phase").value
    }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val r = this.size.minDimension / 2f
            val c = center
            drawCircle(bg, radius = r * 0.84f, center = c)
            drawCircle(bg, radius = r * 0.13f, center = c + Offset(r * 0.84f, -r * 0.72f))
            drawCircle(fg.copy(alpha = 0.22f), radius = r * 0.09f, center = c + Offset(-r * 0.86f, r * 0.50f))
            sparkle(c + Offset(-r * 0.70f, -r * 0.66f), r * 0.15f, fg.copy(alpha = twinkle(phase, 0f)))
            sparkle(c + Offset(r * 0.90f, r * 0.30f), r * 0.10f, fg.copy(alpha = twinkle(phase, 0.4f)))
            sparkle(c + Offset(r * 0.30f, r * 0.92f), r * 0.07f, fg.copy(alpha = twinkle(phase, 0.7f)))
        }
        Box(
            Modifier.gentleFloat().size(size * 0.52f).clip(CircleShape).background(disc),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(size * 0.29f))
        }
    }
}

internal fun twinkle(phase: Float, offset: Float): Float =
    0.35f + 0.65f * (0.5f + 0.5f * sin(2f * PI.toFloat() * (phase + offset)))

/** A four-pointed sparkle. */
internal fun DrawScope.sparkle(c: Offset, s: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y - s)
        quadraticTo(c.x, c.y, c.x + s, c.y)
        quadraticTo(c.x, c.y, c.x, c.y + s)
        quadraticTo(c.x, c.y, c.x - s, c.y)
        quadraticTo(c.x, c.y, c.x, c.y - s)
        close()
    }
    drawPath(p, color)
}

// ── The sky over Today ──────────────────────────────────────────────────────

/** Which sky to paint: it follows the real time of day, like the greeting. */
enum class SkyScene {
    MORNING, DAY, EVENING, NIGHT;

    companion object {
        fun at(hour: Int) = when (hour) {
            in 4..11 -> MORNING
            in 12..16 -> DAY
            in 17..20 -> EVENING
            else -> NIGHT
        }
    }
}

private class SkyPalette(
    val top: Color, val mid: Color, val bottom: Color,
    val hillBack: Color, val hillFront: Color,
    /** text and icons on this sky (contrast checked against the upper sky) */
    val content: Color, val contentSoft: Color,
    val cloud: Color,
)

private fun skyPalette(scene: SkyScene) = when (scene) {
    SkyScene.MORNING -> SkyPalette(
        Color(0xFF8EC9F5), Color(0xFFCFE6F7), Color(0xFFFFE0BD),
        Color(0xFFA9DBA6), Color(0xFF7FC28A),
        Color(0xFF13294B), Color(0xFF34507A), Color.White,
    )
    SkyScene.DAY -> SkyPalette(
        Color(0xFF4FA8EE), Color(0xFF8CCBF6), Color(0xFFD4ECFB),
        Color(0xFF8DD18F), Color(0xFF5DB872),
        Color(0xFF0E2440), Color(0xFF1E3A5F), Color.White,
    )
    SkyScene.EVENING -> SkyPalette(
        Color(0xFF3B3B8F), Color(0xFFB65A9A), Color(0xFFFF9E6B),
        Color(0xFF6B4A8C), Color(0xFF47336B),
        Color.White, Color(0xFFF3E8FF), Color(0xFFFFD9E6),
    )
    SkyScene.NIGHT -> SkyPalette(
        Color(0xFF0B1437), Color(0xFF1B2A63), Color(0xFF33437F),
        Color(0xFF1F2A5C), Color(0xFF141C45),
        Color.White, Color(0xFFD5DCF5), Color(0xFF3A4785),
    )
}

/** The colour to use for text and icons placed on [scene]'s sky. */
fun skyContentColor(scene: SkyScene): Color = skyPalette(scene).content
fun skyContentSoftColor(scene: SkyScene): Color = skyPalette(scene).contentSoft

/**
 * A small painted sky — sunrise, bright day, sunset or a starry night — behind the greeting.
 * Clouds drift, the sun glows and the stars twinkle; the sun (or moon) rises into place when the
 * screen opens. Everything holds still when the phone asks for fewer animations.
 */
@Composable
fun SkyHeader(scene: SkyScene, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val reduce = rememberReduceMotion()
    // The greeting slides in once the sky has started to paint itself
    val words = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        delay(350)
        words.animateTo(1f, tween(Motion.LONG + 200, easing = FastOutSlowInEasing))
    }
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).heightIn(min = 184.dp)) {
        SkyCanvas(scene, Modifier.matchParentSize())
        Column(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = words.value
                    translationX = (1f - words.value) * -18.dp.toPx()
                }
                .padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 12.dp),
            content = content,
        )
    }
}

private val STARS = Random(11).let { r -> List(24) { floatArrayOf(r.nextFloat(), r.nextFloat() * 0.66f, r.nextFloat()) } }

/** A cloud bobbing around (x, y) — kept clear of the greeting so the words stay easy to read. */
private class Cloud(val x: Float, val y: Float, val scale: Float, val swings: Int, val phase: Float)

private val CLOUDS = listOf(
    Cloud(0.66f, 0.16f, 0.78f, 3, 0f),     // drifting past the sun
    Cloud(0.50f, 0.74f, 0.66f, 4, 0.35f),  // low, half behind the hills
)

@Composable
private fun SkyCanvas(scene: SkyScene, modifier: Modifier) {
    val pal = skyPalette(scene)
    val reduce = rememberReduceMotion()
    // One slow clock drives the drifting clouds, the turning rays and the twinkling stars
    val t = if (reduce) 0.35f else {
        val inf = rememberInfiniteTransition(label = "sky")
        inf.animateFloat(0f, 1f, infiniteRepeatable(tween(90_000, easing = LinearEasing)), label = "t").value
    }
    val glow = if (reduce) 0.5f else {
        val inf = rememberInfiniteTransition(label = "glow")
        inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "g").value
    }
    val rise = remember { Animatable(if (reduce) 0f else 1f) }
    LaunchedEffect(Unit) { rise.animateTo(0f, tween(1600, easing = FastOutSlowInEasing)) }
    // The scene assembles itself: hills roll up into place, then clouds and stars appear
    val build = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) { build.animateTo(1f, tween(1400, easing = LinearEasing)) }

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(listOf(pal.top, pal.mid, pal.bottom)))

        // Stars: all night, and the first few in the evening's upper sky
        if (scene == SkyScene.NIGHT || scene == SkyScene.EVENING) {
            STARS.forEach { (x, y, seed) ->
                if (scene == SkyScene.EVENING && y > 0.28f) return@forEach
                val tw = 0.5f + 0.5f * sin(2f * PI.toFloat() * (t * 22f + seed))
                val base = if (scene == SkyScene.NIGHT) 0.35f else 0.25f
                drawCircle(
                    Color.White.copy(alpha = (base + 0.6f * tw).coerceAtMost(1f) * appear(build.value, 0.35f, 0.5f)),
                    radius = (0.9f + seed * 1.5f).dp.toPx(),
                    center = Offset(x * w, y * h),
                )
            }
        }

        // Sun or moon, to the right of the greeting
        val r = 26.dp.toPx()
        val cy = when (scene) {
            SkyScene.MORNING -> 0.40f
            SkyScene.DAY -> 0.30f
            SkyScene.EVENING -> 0.46f
            SkyScene.NIGHT -> 0.32f
        }
        val c = Offset(w * 0.80f, h * cy + rise.value * 46.dp.toPx())
        if (scene == SkyScene.NIGHT) {
            val moon = Color(0xFFF5F1DC)
            drawCircle(Color.White.copy(alpha = 0.04f + 0.04f * glow), radius = r * 2.1f, center = c)
            drawCircle(Color.White.copy(alpha = 0.07f), radius = r * 1.45f, center = c)
            val disc = Path().apply { addOval(Rect(c, r)) }
            val bite = Path().apply { addOval(Rect(c + Offset(r * 0.48f, -r * 0.32f), r * 0.92f)) }
            drawPath(Path.combine(PathOperation.Difference, disc, bite), moon)
        } else {
            val sun = when (scene) {
                SkyScene.MORNING -> Color(0xFFFFB547)
                SkyScene.DAY -> Color(0xFFFFD43B)
                else -> Color(0xFFFF8A4C)
            }
            // a light, warm glow (white-ish over the sky, never a grey ring)
            val halo = if (scene == SkyScene.EVENING) Color(0xFFFFD2B0) else Color(0xFFFFF4C2)
            drawCircle(halo.copy(alpha = 0.16f + 0.10f * glow), radius = r * (1.9f + 0.15f * glow), center = c)
            drawCircle(halo.copy(alpha = 0.38f), radius = r * 1.4f, center = c)
            if (scene != SkyScene.EVENING) {
                rotate(t * 360f, pivot = c) {
                    repeat(12) { i ->
                        val a = i * (PI.toFloat() / 6f)
                        drawLine(
                            sun.copy(alpha = 0.75f),
                            start = c + Offset(cos(a) * r * 1.62f, sin(a) * r * 1.62f),
                            end = c + Offset(cos(a) * r * 1.95f, sin(a) * r * 1.95f),
                            strokeWidth = 3.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }
            drawCircle(sun, radius = r, center = c)
        }

        // Clouds bobbing slowly from side to side
        if (scene != SkyScene.NIGHT) {
            val unit = 1.dp.toPx()
            CLOUDS.forEach { cl ->
                val x = cl.x * w + sin(2f * PI.toFloat() * (t * cl.swings + cl.phase)) * 22f * unit
                val shown = appear(build.value, 0.3f, 0.45f)
                cloud(
                    Offset(x - (1f - shown) * 24f * unit, cl.y * h),
                    cl.scale * unit,
                    pal.cloud.copy(alpha = (if (scene == SkyScene.EVENING) 0.6f else 0.92f) * shown),
                )
            }
        }

        // Two rolling hills
        val back = Path().apply {
            moveTo(0f, h * 0.80f)
            cubicTo(w * 0.25f, h * 0.64f, w * 0.45f, h * 0.70f, w * 0.62f, h * 0.79f)
            cubicTo(w * 0.78f, h * 0.87f, w * 0.90f, h * 0.70f, w, h * 0.74f)
            lineTo(w, h); lineTo(0f, h); close()
        }
        translate(top = (1f - easeOutCubic(appear(build.value, 0f, 0.6f))) * h * 0.4f) { drawPath(back, pal.hillBack) }
        val front = Path().apply {
            moveTo(0f, h * 0.91f)
            cubicTo(w * 0.20f, h * 0.81f, w * 0.42f, h * 0.83f, w * 0.60f, h * 0.93f)
            cubicTo(w * 0.75f, h * 1.01f, w * 0.88f, h * 0.85f, w, h * 0.89f)
            lineTo(w, h); lineTo(0f, h); close()
        }
        translate(top = (1f - easeOutCubic(appear(build.value, 0.12f, 0.6f))) * h * 0.5f) { drawPath(front, pal.hillFront) }
    }
}

/** 0 → 1 across [start, start + length] of an entrance. */
private fun appear(progress: Float, start: Float, length: Float) = ((progress - start) / length).coerceIn(0f, 1f)

private fun easeOutCubic(v: Float) = 1f - (1f - v) * (1f - v) * (1f - v)

/** A puffy cloud whose bottom-left is at [o]; [u] is one unit (1dp × scale). */
private fun DrawScope.cloud(o: Offset, u: Float, color: Color) {
    drawCircle(color, radius = 16f * u, center = Offset(o.x + 24f * u, o.y))
    drawCircle(color, radius = 22f * u, center = Offset(o.x + 48f * u, o.y - 9f * u))
    drawCircle(color, radius = 15f * u, center = Offset(o.x + 72f * u, o.y - 1f * u))
    drawRoundRect(
        color,
        topLeft = Offset(o.x + 8f * u, o.y - 2f * u),
        size = Size(80f * u, 16f * u),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f * u),
    )
}

// ── Progress ────────────────────────────────────────────────────────────────

/** A round progress ring with something in the middle. */
@Composable
fun ProgressRing(
    progress: Float,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 76.dp,
    stroke: Dp = 9.dp,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val sw = stroke.toPx()
            val topLeft = Offset(sw / 2f, sw / 2f)
            val arc = Size(this.size.width - sw, this.size.height - sw)
            drawArc(trackColor, 0f, 360f, false, topLeft, arc, style = Stroke(sw))
            val p = progress.coerceIn(0f, 1f)
            if (p > 0f) drawArc(color, -90f, 360f * p, false, topLeft, arc, style = Stroke(sw, cap = StrokeCap.Round))
        }
        content()
    }
}

// ── Little celebrations ─────────────────────────────────────────────────────

/** A ring and sparks bursting out once from behind something — "done!" for a dose taken. */
@Composable
fun BurstEffect(color: Color, modifier: Modifier = Modifier, onFinished: () -> Unit = {}) {
    if (rememberReduceMotion()) {
        LaunchedEffect(Unit) { onFinished() }
        return
    }
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        p.animateTo(1f, tween(800, easing = LinearOutSlowInEasing))
        onFinished()
    }
    Canvas(modifier) {
        val t = p.value
        val r = size.minDimension / 2f
        drawCircle(
            color.copy(alpha = (1f - t) * 0.9f),
            radius = r * (0.95f + 0.65f * t),
            center = center,
            style = Stroke((1f - t) * 5.dp.toPx() + 0.5f),
        )
        repeat(8) { i ->
            val a = i * (PI.toFloat() / 4f) + 0.39f
            val d = r * (1.05f + 0.8f * t)
            drawCircle(
                color.copy(alpha = 1f - t),
                radius = (1f - t) * 4.dp.toPx() + 0.5f,
                center = center + Offset(cos(a) * d, sin(a) * d),
            )
        }
    }
}

/** A whole number that counts up to [target] — for totals and percentages. */
@Composable
fun animatedCount(target: Int, durationMillis: Int = 900): Int {
    val reduce = rememberReduceMotion()
    val value = remember { Animatable(if (reduce) target.toFloat() else 0f) }
    LaunchedEffect(target) {
        if (reduce) value.snapTo(target.toFloat())
        else value.animateTo(target.toFloat(), tween(durationMillis, easing = FastOutSlowInEasing))
    }
    return value.value.roundToInt()
}
