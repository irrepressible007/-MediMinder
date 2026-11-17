package com.example.mediminder.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mediminder.ui.theme.Hue
import com.example.mediminder.ui.theme.color
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/*
 * "It worked": a short banner that slides down whenever something is added, saved, stopped or
 * deleted — wherever the person happens to be — with a little animated icon and a soft buzz.
 */

enum class FeedbackKind { ADDED, SAVED, DELETED, STOPPED, RESUMED }

data class Feedback(
    val kind: FeedbackKind,
    @param:StringRes val text: Int,
    val arg: String? = null,
    val stamp: Long = System.nanoTime(),
)

object FeedbackBus {
    private val _events = MutableSharedFlow<Feedback>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<Feedback> = _events

    fun show(kind: FeedbackKind, @StringRes text: Int, arg: String? = null) {
        _events.tryEmit(Feedback(kind, text, arg))
    }

    /** A medicine just added or changed, so its row can glow once in the list (id to when). */
    private val _highlight = MutableStateFlow<Pair<Long, Long>?>(null)
    val highlight: StateFlow<Pair<Long, Long>?> = _highlight

    fun highlight(medicationId: Long) {
        _highlight.value = medicationId to System.currentTimeMillis()
    }

    fun clearHighlight() {
        _highlight.value = null
    }
}

/** Shows [FeedbackBus] messages; placed once, over every screen. */
@Composable
fun FeedbackHost(modifier: Modifier = Modifier) {
    var current by remember { mutableStateOf<Feedback?>(null) }
    var last by remember { mutableStateOf<Feedback?>(null) }   // kept while it slides away
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(Unit) {
        FeedbackBus.events.collect {
            last = it
            current = it
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        }
    }
    LaunchedEffect(current) {
        if (current != null) {
            delay(2800)
            current = null
        }
    }
    AnimatedVisibility(
        visible = current != null,
        modifier = modifier,
        enter = slideInVertically(spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)) { -it * 2 } + fadeIn(tween(Motion.SHORT)),
        exit = slideOutVertically(tween(Motion.MEDIUM)) { -it * 2 } + fadeOut(tween(Motion.MEDIUM)),
    ) {
        val f = last ?: return@AnimatedVisibility
        FeedbackBanner(f, onDismiss = { current = null })
    }
}

@Composable
private fun FeedbackBanner(f: Feedback, onDismiss: () -> Unit) {
    val (icon, hue) = when (f.kind) {
        FeedbackKind.ADDED -> Icons.Default.AddCircle to Hue.GREEN
        FeedbackKind.SAVED -> Icons.Default.CheckCircle to Hue.SKY
        FeedbackKind.DELETED -> Icons.Default.Delete to Hue.ROSE
        FeedbackKind.STOPPED -> Icons.Default.PauseCircle to Hue.AMBER
        FeedbackKind.RESUMED -> Icons.Default.PlayCircle to Hue.GREEN
    }
    val text = if (f.arg != null) stringResource(f.text, f.arg) else stringResource(f.text)
    Surface(
        onClick = onDismiss,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 10.dp,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(Modifier.padding(start = 10.dp, end = 20.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            FeedbackIcon(f, icon, hue)
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2)
        }
    }
}

/** The icon pops in (a "+" spins into place, a bin shakes) while a ring ripples out once. */
@Composable
private fun FeedbackIcon(f: Feedback, icon: androidx.compose.ui.graphics.vector.ImageVector, hue: Hue) {
    val reduce = rememberReduceMotion()
    val pop = remember(f.stamp) { Animatable(if (reduce) 1f else 0.3f) }
    val turn = remember(f.stamp) { Animatable(if (!reduce && f.kind == FeedbackKind.ADDED) -120f else 0f) }
    val ripple = remember(f.stamp) { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(f.stamp) {
        if (reduce) return@LaunchedEffect
        launch { pop.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMediumLow)) }
        launch { ripple.animateTo(1f, tween(900, easing = LinearOutSlowInEasing)) }
        launch {
            when (f.kind) {
                FeedbackKind.ADDED -> turn.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow))
                FeedbackKind.DELETED -> {
                    delay(180)
                    turn.animateTo(0f, keyframes {
                        durationMillis = 650
                        -16f at 100
                        14f at 230
                        -9f at 360
                        5f at 480
                        0f at 650
                    })
                }
                else -> Unit
            }
        }
    }
    val ring = hue.color
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(44.dp)) {
            val t = ripple.value
            if (t < 1f) {
                drawCircle(
                    ring.copy(alpha = (1f - t) * 0.6f),
                    radius = size.minDimension / 2f * (0.8f + 0.6f * t),
                    style = Stroke((1f - t) * 4.dp.toPx() + 0.5f),
                )
            }
        }
        IconBadge(
            icon, hue,
            size = 44.dp,
            modifier = Modifier.graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
                rotationZ = turn.value
            },
        )
    }
}
