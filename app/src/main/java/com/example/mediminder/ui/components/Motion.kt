package com.example.mediminder.ui.components

import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/*
 * MediMinder's motion: calm, short and purposeful — things arrive, respond to a touch, and
 * celebrate a finished day. Nothing loops except where attention is needed (a dose that is
 * due, a ringing alarm). With the phone's "Remove animations" setting on, the decorative
 * parts are switched off.
 */

object Motion {
    const val SHORT = 220
    const val MEDIUM = 360
    const val LONG = 520
}

/** True when the user asked the phone to remove animations (Accessibility). */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
}

// ── Arrival ─────────────────────────────────────────────────────────────────

/** Remembers when a screen opened, so only what shows at that moment animates in. */
@Stable
class EntranceState(val enabled: Boolean) {
    val openedAt: Long = SystemClock.uptimeMillis()
}

/** [key]: when it changes (e.g. another day is shown), things arrive again. */
@Composable
fun rememberEntranceState(key: Any? = Unit): EntranceState {
    val reduce = rememberReduceMotion()
    return remember(key) { EntranceState(!reduce) }
}

/**
 * Wraps a list item so it arrives with the screen's stagger. A Column, not a Box: an item
 * may hold several things (a title, a hint, a chart) that must stay one under another.
 */
@Composable
fun Arrive(state: EntranceState, index: Int, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Column(Modifier.entrance(state, index)) { content() }
}

/**
 * Fades and lifts an item into place, one after another ([index] staggers it). Items that
 * appear later — by scrolling — show at once, so scrolling never feels sluggish.
 */
fun Modifier.entrance(state: EntranceState, index: Int): Modifier = composed {
    val animate = remember { state.enabled && index < 10 && SystemClock.uptimeMillis() - state.openedAt < 700 }
    if (!animate) return@composed this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 45L)
        progress.animateTo(1f, tween(Motion.LONG - 100, easing = FastOutSlowInEasing))
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 22.dp.toPx()
    }
}

// ── Touch ───────────────────────────────────────────────────────────────────

/** Gives pressable things a soft "push" so a touch is felt, not just seen. */
fun Modifier.pressScale(interaction: InteractionSource, pressedScale: Float = 0.97f): Modifier = composed {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) pressedScale else 1f,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "press",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

// ── Attention ───────────────────────────────────────────────────────────────

/** A slow up-and-down float, for the picture on an empty screen. */
fun Modifier.gentleFloat(): Modifier = composed {
    if (rememberReduceMotion()) return@composed this
    val t = rememberInfiniteTransition(label = "float")
    val y by t.animateFloat(-5f, 5f, infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "y")
    graphicsLayer { translationY = y.dp.toPx() }
}

/** A short bell-like shake every couple of seconds, for the ringing alarm. */
fun Modifier.ringing(): Modifier = composed {
    if (rememberReduceMotion()) return@composed this
    val t = rememberInfiniteTransition(label = "ring")
    val angle by t.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 2000
                0f at 0
                -14f at 90
                14f at 200
                -10f at 310
                10f at 420
                -5f at 520
                0f at 620
            }
        ),
        label = "angle",
    )
    graphicsLayer { rotationZ = angle }
}

/** 0.4 → 1 → 0.4, for gently pulsing a border that needs to be noticed. */
@Composable
fun rememberPulse(enabled: Boolean, periodMillis: Int = 1400): Float {
    if (!enabled || rememberReduceMotion()) return 1f
    val t = rememberInfiniteTransition(label = "pulse")
    val v by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(periodMillis / 2, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "v")
    return v
}

/** Rings that spread out from behind [content] — the "this is ringing" signal. */
@Composable
fun PulseRings(color: Color, size: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val reduce = rememberReduceMotion()
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        if (!reduce) {
            val t = rememberInfiniteTransition(label = "rings")
            val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "phase")
            Canvas(Modifier.size(size)) {
                val maxR = this.size.minDimension / 2f
                repeat(3) { i ->
                    val p = (phase + i / 3f) % 1f
                    drawCircle(color.copy(alpha = (1f - p) * 0.35f), radius = maxR * (0.45f + 0.55f * p))
                }
            }
        }
        content()
    }
}

// ── Celebration ─────────────────────────────────────────────────────────────

private class Particle(val angle: Float, val speed: Float, val color: Color, val w: Float, val h: Float, val spin: Float)

/**
 * A short burst of confetti (about 1.6 s) — shown once when the last dose of the day is
 * taken. Draws over whatever it is placed on; it never blocks touches.
 */
@Composable
fun CelebrationBurst(colors: List<Color>, modifier: Modifier = Modifier, onFinished: () -> Unit = {}) {
    if (rememberReduceMotion()) {
        LaunchedEffect(Unit) { onFinished() }
        return
    }
    val particles = remember {
        val r = Random(System.nanoTime())
        List(64) {
            Particle(
                angle = Math.toRadians(-90.0 + r.nextDouble(-78.0, 78.0)).toFloat(),
                speed = 0.45f + r.nextFloat() * 0.75f,
                color = colors[it % colors.size],
                w = 6f + r.nextFloat() * 6f,
                h = 10f + r.nextFloat() * 8f,
                spin = r.nextFloat() * 2f - 1f,
            )
        }
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(2000, easing = LinearEasing))
        onFinished()
    }
    // Drawn beyond the card's edges on purpose: the pieces fly up over the header and fall back
    Canvas(modifier) {
        val t = progress.value
        val reach = 300.dp.toPx()           // how far the fastest piece travels
        val gravity = 380.dp.toPx()
        val origin = Offset(size.width / 2f, size.height * 0.45f)
        particles.forEach { p ->
            // fast at first, then slowing as if through air
            val travel = p.speed * reach * (1f - (1f - t) * (1f - t))
            val x = origin.x + cos(p.angle) * travel
            val y = origin.y + sin(p.angle) * travel + gravity * t * t
            val alpha = (1f - t * t).coerceIn(0f, 1f)
            rotate(p.spin * 540f * t, pivot = Offset(x, y)) {
                drawRoundRect(
                    p.color.copy(alpha = alpha),
                    topLeft = Offset(x - p.w.dp.toPx() / 2, y - p.h.dp.toPx() / 2),
                    size = Size(p.w.dp.toPx(), p.h.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            }
        }
    }
}

// ── Changing content ────────────────────────────────────────────────────────

/** Text whose new value rolls in from below (counts, "2 of 3 taken"). */
@Composable
fun <T> RollingContent(target: T, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    AnimatedContent(
        targetState = target,
        modifier = modifier,
        transitionSpec = {
            (slideInVertically(tween(Motion.MEDIUM)) { it / 2 } + fadeIn(tween(Motion.MEDIUM)))
                .togetherWith(slideOutVertically(tween(Motion.SHORT)) { -it / 2 } + fadeOut(tween(Motion.SHORT)))
        },
        label = "rolling",
    ) { content(it) }
}

/** Page-to-page inside a flow (wizard, onboarding): forward slides left, back slides right. */
fun <S> AnimatedContentTransitionScope<S>.stepTransition(forward: Boolean): ContentTransform {
    val dir = if (forward) 1 else -1
    return (slideInHorizontally(tween(Motion.MEDIUM, easing = FastOutSlowInEasing)) { dir * it / 3 } + fadeIn(tween(Motion.MEDIUM)))
        .togetherWith(slideOutHorizontally(tween(Motion.SHORT)) { -dir * it / 4 } + fadeOut(tween(Motion.SHORT)))
}

/** Grows in with a soft bounce shortly after the screen opens — for an "Add" button. */
fun Modifier.popOnEnter(delayMillis: Long = 200): Modifier = composed {
    val reduce = rememberReduceMotion()
    val p = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reduce) return@LaunchedEffect
        delay(delayMillis)
        p.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow))
    }
    graphicsLayer {
        val v = p.value
        scaleX = 0.6f + 0.4f * v
        scaleY = 0.6f + 0.4f * v
        alpha = v.coerceIn(0f, 1f)
    }
}

/** A check that pops in with a little bounce. */
fun popIn() = scaleIn(spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium), initialScale = 0.4f) + fadeIn(tween(Motion.SHORT))
